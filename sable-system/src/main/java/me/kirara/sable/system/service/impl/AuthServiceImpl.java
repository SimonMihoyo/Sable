package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.system.auth.AuthProperties;
import me.kirara.sable.system.auth.JwtPayload;
import me.kirara.sable.system.auth.JwtUtil;
import me.kirara.sable.system.auth.UserAuthority;
import me.kirara.sable.system.dto.LoginRequest;
import me.kirara.sable.system.dto.LoginResponse;
import me.kirara.sable.system.entity.PasswordHistory;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.mapper.PasswordHistoryMapper;
import me.kirara.sable.system.mapper.SysUserMapper;
import me.kirara.sable.system.service.AuthService;
import me.kirara.sable.system.service.TokenRevocationService;
import me.kirara.sable.system.service.UserAuthorityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现。
 *
 * <p>已实现：登录主流程（查用户 → 状态与锁定校验 → BCrypt 校验密码 → 失败计数，
 * 达阈值锁定 → 成功则重置计数并签发双令牌）、刷新令牌（轮换签发）、修改密码
 * （校验旧密码 → 校验复杂度 → 比对当前密码与最近 N 次历史 → 更新哈希并留档）、
 * 登出吊销（按 jti 写入吊销表，见 {@link TokenRevocationService}）。
 * 令牌中装配租户与角色，归属关系由 {@link UserAuthorityService} 解析。</p>
 *
 * <p>尚未实现：MFA 校验需引入 TOTP 依赖，目前对已开启 MFA 的账号明确拒绝而不是静默放行；
 * 令牌中的权限（perm）声明暂为空数组，待接口级鉴权落地时装配。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** 用户状态：正常。 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** 用户状态：管理员锁定。 */
    private static final String STATUS_LOCKED = "LOCKED";

    /** 用户状态：已停用。 */
    private static final String STATUS_DISABLED = "DISABLED";

    /** 失败提示统一文案 —— 用户不存在与密码错误共用，避免暴露账号是否存在。 */
    private static final String MSG_LOGIN_FAILED = "用户名或密码错误";

    /** 新密码最少长度（对应 API 设计文档 2.4 节的校验规则）。 */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final SysUserMapper sysUserMapper;
    private final PasswordHistoryMapper passwordHistoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthProperties authProperties;
    private final TokenRevocationService tokenRevocationService;
    private final UserAuthorityService userAuthorityService;

    public AuthServiceImpl(SysUserMapper sysUserMapper, PasswordHistoryMapper passwordHistoryMapper,
                           PasswordEncoder passwordEncoder, JwtUtil jwtUtil, AuthProperties authProperties,
                           TokenRevocationService tokenRevocationService,
                           UserAuthorityService userAuthorityService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordHistoryMapper = passwordHistoryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authProperties = authProperties;
        this.tokenRevocationService = tokenRevocationService;
        this.userAuthorityService = userAuthorityService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = findByUsername(request.getUsername());
        if (user == null) {
            log.warn("登录失败：用户不存在 username={}", request.getUsername());
            throw new BusinessException(ErrorCode.AUTH_FAILED.getCode(), MSG_LOGIN_FAILED);
        }

        ensureAccountUsable(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw recordLoginFailure(user);
        }

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            // TODO 接入 TOTP 校验（需引入依赖，见 AGENTS.md）；在此之前明确拒绝，不放行
            log.warn("登录中断：账号已开启 MFA，但 TOTP 校验尚未实现 userId={}", user.getId());
            throw new BusinessException(ErrorCode.MFA_REQUIRED);
        }

        markLoginSuccess(user);
        LoginResponse response = issueTokens(user);
        log.info("登录成功 userId={} username={}", user.getId(), user.getUsername());
        return response;
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // parse 内部已区分「过期」与「非法」两种错误码
        JwtPayload payload = jwtUtil.parse(refreshToken);
        if (!JwtUtil.TYPE_REFRESH.equals(payload.getType())) {
            log.warn("刷新失败：令牌类型不是 refresh，实际为 {}", payload.getType());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        SysUser user = findById(payload.getUserId());
        if (user == null) {
            log.warn("刷新失败：用户不存在或已删除 userId={}", payload.getUserId());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        // 该刷新令牌可能已被登出吊销 —— 吊销优先于有效期，这也是登出能真正生效的关键
        if (tokenRevocationService.isRevoked(payload.getJti())) {
            log.warn("刷新失败：刷新令牌已被吊销 userId={} jti={}", user.getId(), payload.getJti());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        // 令牌还在有效期内，但账号已被停用 / 锁定 —— 不应再续期
        ensureAccountUsable(user);

        LoginResponse response = issueTokens(user);
        log.info("令牌刷新成功 userId={}", user.getId());
        return response;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        revokeQuietly(accessToken, JwtUtil.TYPE_ACCESS);
        revokeQuietly(refreshToken, JwtUtil.TYPE_REFRESH);
    }

    /**
     * 吊销单枚令牌，失败不影响登出结果。
     *
     * <p>登出是幂等操作：客户端可能重复调用、可能拿着早已过期的令牌调用，
     * 这些都不该报错 —— 令牌最终是无效的，结果与用户的预期一致。</p>
     *
     * @param token        待吊销的令牌，可为空
     * @param expectedType 期望的令牌类型，类型不符则忽略（防止把刷新令牌当访问令牌传）
     */
    private void revokeQuietly(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return;
        }

        JwtPayload payload;
        try {
            payload = jwtUtil.parse(token);
        } catch (BusinessException e) {
            // 已过期或非法：本来就通不过校验，无需吊销，记 debug 即可
            log.debug("登出忽略无效令牌 type={} 原因={}", expectedType, e.getMessage());
            return;
        }

        if (!expectedType.equals(payload.getType())) {
            log.warn("登出忽略类型不符的令牌：期望 {} 实际 {}", expectedType, payload.getType());
            return;
        }

        tokenRevocationService.revoke(payload.getJti(), payload.getUserId(), payload.getType(),
                payload.getExpiresAt(), TokenRevocationService.REASON_LOGOUT);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            // 控制器从安全上下文取用户；取不到说明请求未携带有效令牌
            log.warn("改密失败：未取得当前用户身份");
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        SysUser user = findById(userId);
        if (user == null) {
            log.warn("改密失败：用户不存在或已删除 userId={}", userId);
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            log.warn("改密失败：旧密码错误 userId={}", userId);
            throw new BusinessException(ErrorCode.AUTH_FAILED.getCode(), "旧密码错误");
        }

        validatePasswordStrength(newPassword);
        rejectReusedPassword(userId, newPassword, user.getPasswordHash());

        String newHash = passwordEncoder.encode(newPassword);
        LocalDateTime now = LocalDateTime.now();
        // update(entity, wrapper) 传 null 实体不触达 BaseEntity 自动填充，故显式写 updated_at
        sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPasswordHash, newHash)
                .set(SysUser::getPasswordChangedAt, now)
                .set(SysUser::getUpdatedAt, now));

        PasswordHistory history = new PasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(newHash);
        history.setChangedAt(now);
        passwordHistoryMapper.insert(history);

        log.info("改密成功 userId={}", userId);
    }

    /**
     * 按用户名查询未删除的用户。
     *
     * <p>username 在库中有唯一约束，故至多命中一条。</p>
     */
    private SysUser findByUsername(String username) {
        return sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .isNull(SysUser::getDeletedAt));
    }

    /** 按主键查询未删除的用户。 */
    private SysUser findById(Long userId) {
        return sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getId, userId)
                .isNull(SysUser::getDeletedAt));
    }

    /**
     * 校验账号当前是否允许签发令牌：已停用、管理员锁定、以及密码连续错误导致的临时锁定均拒绝。
     *
     * @throws BusinessException 停用抛「账号已停用」，锁定抛 {@link ErrorCode#ACCOUNT_LOCKED}
     */
    private void ensureAccountUsable(SysUser user) {
        if (STATUS_DISABLED.equals(user.getStatus())) {
            log.warn("账号不可用：账号已停用 userId={}", user.getId());
            throw new BusinessException(ErrorCode.AUTH_FAILED.getCode(), "账号已停用");
        }
        if (STATUS_LOCKED.equals(user.getStatus())) {
            log.warn("账号不可用：账号被管理员锁定 userId={}", user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            log.warn("账号不可用：账号锁定中 userId={} lockedUntil={}", user.getId(), user.getLockedUntil());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    /**
     * 记录一次密码错误：累加失败次数，达到阈值则锁定账号。
     *
     * <p>锁定后失败次数归零 —— 锁定期满后用户重新获得完整的尝试次数，
     * 否则剩余次数为 0 会导致再失败一次就立刻重新锁定。</p>
     *
     * @return 交由调用方抛出的业务异常（密码错误，或本次触发了锁定）
     */
    private BusinessException recordLoginFailure(SysUser user) {
        int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        boolean locked = failCount >= authProperties.getMaxLoginFail();

        // 注意：update(entity, wrapper) 传 null 实体不会触达 BaseEntity 的自动填充，故显式写入 updated_at
        LambdaUpdateWrapper<SysUser> update = Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLoginFailCount, locked ? 0 : failCount)
                .set(SysUser::getUpdatedAt, now);
        if (locked) {
            update.set(SysUser::getLockedUntil, now.plus(authProperties.getLockDuration()));
        }
        sysUserMapper.update(null, update);

        if (locked) {
            log.warn("登录失败：连续失败达 {} 次，账号锁定 userId={}", authProperties.getMaxLoginFail(), user.getId());
            return new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        log.warn("登录失败：密码错误 userId={} 累计失败={}", user.getId(), failCount);
        return new BusinessException(ErrorCode.AUTH_FAILED.getCode(), MSG_LOGIN_FAILED);
    }

    /**
     * 登录成功后的状态回写：重置失败计数、清除临时锁定、记录登录时间。
     *
     * <p>last_login_ip 需要请求上下文，当前接口签名未携带，待认证过滤器接入后补齐。</p>
     */
    private void markLoginSuccess(SysUser user) {
        LocalDateTime now = LocalDateTime.now();
        sysUserMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getLastLoginAt, now)
                .set(SysUser::getUpdatedAt, now));
    }

    /**
     * 校验新密码复杂度 —— 最少 8 位，且同时包含大写字母、小写字母与数字。
     *
     * <p>规则来自 API 设计文档 2.4 节。字符集不做限制，允许符号。</p>
     */
    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                    "新密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位");
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasUpper || !hasLower || !hasDigit) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                    "新密码必须包含大写字母、小写字母和数字");
        }
    }

    /**
     * 拒绝复用近期密码：既不能与当前密码相同，也不能与最近 N 条历史相同。
     *
     * <p>比对当前密码是必须的 —— 对于在引入历史表之前就已存在的账号，
     * 其当前密码并未记录在 {@code password_history} 中。</p>
     */
    private void rejectReusedPassword(Long userId, String newPassword, String currentHash) {
        int historySize = authProperties.getPasswordHistorySize();

        if (passwordEncoder.matches(newPassword, currentHash)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "新密码不能与当前密码相同");
        }

        // BCrypt 每次加盐，哈希值不同，只能逐个比对而不能用等值查询
        for (PasswordHistory history : recentPasswords(userId, historySize)) {
            if (passwordEncoder.matches(newPassword, history.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(),
                        "新密码不能与最近 " + historySize + " 次密码相同");
            }
        }
    }

    /** 取该用户最近 N 条密码历史，最近的在前。 */
    private List<PasswordHistory> recentPasswords(Long userId, int size) {
        Page<PasswordHistory> page = new Page<>(1, Math.max(size, 1));
        return passwordHistoryMapper.selectPage(page, Wrappers.<PasswordHistory>lambdaQuery()
                .eq(PasswordHistory::getUserId, userId)
                .orderByDesc(PasswordHistory::getId)).getRecords();
    }

    /**
     * 签发访问令牌与刷新令牌。
     *
     * <p>每次签发都重新解析租户与角色归属，保证续期不会拖着过期的归属关系。
     * 权限声明暂固定为空数组：{@code role.permissions} 是 JSONB 列，
     * 要正确读出并解析需先处理类型映射，而权限的真正消费者（接口级鉴权）尚未实现，
     * 现在装配容易装错形态 —— 留待鉴权落地时统一处理。此处仍写入空数组，
     * 是为了让令牌形状与 API 设计文档 1.4 节一致，前端可以无条件读该字段。</p>
     */
    private LoginResponse issueTokens(SysUser user) {
        UserAuthority authority = userAuthorityService.resolve(user.getId());

        String accessToken = jwtUtil.issueAccessToken(user.getId(), user.getUsername(),
                authority.getTenantId(), authority.getRoleNames(), List.of());
        String refreshToken = jwtUtil.issueRefreshToken(user.getId());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(authProperties.getAccessTokenTtl().toSeconds());
        response.setMfaRequired(false);
        return response;
    }
}
