package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import me.kirara.sable.system.service.TokenRevocationService;
import me.kirara.sable.system.service.UserAuthorityService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AuthServiceImpl} 单元测试。
 *
 * <p>Mapper 用 Mockito 模拟，密码编码与令牌签发使用真实实现（BCrypt 强度降到 4 以加速）。</p>
 */
@SuppressWarnings("unchecked")
class AuthServiceImplTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long";
    private static final String RAW_PASSWORD = "Sable@2026";
    private static final String NEW_PASSWORD = "NewP@ss456";
    private static final int MAX_LOGIN_FAIL = 3;
    private static final long USER_ID = 42L;

    private SysUserMapper sysUserMapper;
    private PasswordHistoryMapper passwordHistoryMapper;
    private TokenRevocationService tokenRevocationService;
    private UserAuthorityService userAuthorityService;
    private PasswordEncoder passwordEncoder;
    private AuthProperties authProperties;
    private JwtUtil jwtUtil;
    private AuthServiceImpl authService;
    private String passwordHash;

    @BeforeEach
    void setUp() {
        // lambdaQuery / lambdaUpdate 靠 TableInfo 缓存把方法引用映射成列名，
        // 该缓存在 Spring 启动时自动建立；纯单测环境需手动初始化一次，
        // 否则会抛 "can not find lambda cache for this entity"。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, PasswordHistory.class);

        sysUserMapper = mock(SysUserMapper.class);
        passwordHistoryMapper = mock(PasswordHistoryMapper.class);
        tokenRevocationService = mock(TokenRevocationService.class);
        userAuthorityService = mock(UserAuthorityService.class);
        // 默认无租户归属；需要断言装配结果的用例再单独打桩
        when(userAuthorityService.resolve(any())).thenReturn(new UserAuthority());
        passwordEncoder = new BCryptPasswordEncoder(4);
        passwordHash = passwordEncoder.encode(RAW_PASSWORD);

        authProperties = new AuthProperties();
        authProperties.setSecret(SECRET);
        authProperties.setAccessTokenTtl(Duration.ofMinutes(30));
        authProperties.setMaxLoginFail(MAX_LOGIN_FAIL);
        authProperties.setLockDuration(Duration.ofMinutes(15));
        authProperties.setPasswordHistorySize(3);

        jwtUtil = new JwtUtil(authProperties);
        authService = new AuthServiceImpl(sysUserMapper, passwordHistoryMapper,
                passwordEncoder, jwtUtil, authProperties, tokenRevocationService, userAuthorityService);
    }

    // ------------------------------------------------------------------ 登录

    @Test
    @DisplayName("密码正确时签发双令牌，载荷含用户身份且租户暂为空")
    void loginSuccess() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());

        LoginResponse response = authService.login(loginRequest(RAW_PASSWORD));

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals(authProperties.getAccessTokenTtl().toSeconds(), response.getExpiresIn());
        assertFalse(response.isMfaRequired());

        JwtPayload payload = jwtUtil.parse(response.getAccessToken());
        assertEquals(USER_ID, payload.getUserId().longValue());
        assertEquals("alice", payload.getUsername());
        assertEquals(JwtUtil.TYPE_ACCESS, payload.getType());
        assertNull(payload.getTenantId());
        assertTrue(payload.getRoles().isEmpty());
        assertTrue(payload.getPermissions().isEmpty());

        assertEquals(JwtUtil.TYPE_REFRESH, jwtUtil.parse(response.getRefreshToken()).getType());
    }

    @Test
    @DisplayName("连续两次登录签发的访问令牌 jti 不同（同秒也不会被误吊销）")
    void loginIssuesDistinctTokenIds() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());

        String firstAccess = authService.login(loginRequest(RAW_PASSWORD)).getAccessToken();
        String secondAccess = authService.login(loginRequest(RAW_PASSWORD)).getAccessToken();

        String firstJti = jwtUtil.parse(firstAccess).getJti();
        assertNotNull(firstJti);
        assertNotEquals(firstJti, jwtUtil.parse(secondAccess).getJti());
    }

    @Test
    @DisplayName("登录时把解析到的租户与角色装配进访问令牌，权限留空数组")
    void loginAssemblesTenantAndRoles() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());
        when(userAuthorityService.resolve(USER_ID)).thenReturn(authority(1001L, "COMPLIANCE_OFFICER"));

        LoginResponse response = authService.login(loginRequest(RAW_PASSWORD));

        JwtPayload payload = jwtUtil.parse(response.getAccessToken());
        assertEquals(1001L, payload.getTenantId().longValue());
        assertEquals(List.of("COMPLIANCE_OFFICER"), payload.getRoles());
        assertTrue(payload.getPermissions().isEmpty(), "权限声明本轮固定为空数组");
        // 刷新令牌只承载用户主体，不带业务声明
        assertNull(jwtUtil.parse(response.getRefreshToken()).getTenantId());
    }

    @Test
    @DisplayName("登录成功时重置失败计数、清除锁定并记录登录时间")
    void loginSuccessResetsFailState() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());

        authService.login(loginRequest(RAW_PASSWORD));

        String sqlSet = capturedSetClause();
        assertTrue(sqlSet.contains("login_fail_count"), "应重置失败计数：" + sqlSet);
        assertTrue(sqlSet.contains("locked_until"), "应清除锁定时间：" + sqlSet);
        assertTrue(sqlSet.contains("last_login_at"), "应记录登录时间：" + sqlSet);
    }

    @Test
    @DisplayName("用户不存在时返回统一的用户名或密码错误")
    void loginWithUnknownUser() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest(RAW_PASSWORD)));

        assertEquals(ErrorCode.AUTH_FAILED.getCode(), ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(sysUserMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("密码错误但未达阈值时只累加失败次数，不锁定")
    void loginWithWrongPasswordUnderThreshold() {
        SysUser user = activeUser();
        user.setLoginFailCount(0);
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("wrong-password")));

        assertEquals(ErrorCode.AUTH_FAILED.getCode(), ex.getCode());
        String sqlSet = capturedSetClause();
        assertTrue(sqlSet.contains("login_fail_count"), "应累加失败次数：" + sqlSet);
        assertFalse(sqlSet.contains("locked_until"), "未达阈值不应锁定：" + sqlSet);
    }

    @Test
    @DisplayName("密码错误达到阈值时锁定账号并返回账号已锁定")
    void loginWithWrongPasswordReachesThreshold() {
        SysUser user = activeUser();
        user.setLoginFailCount(MAX_LOGIN_FAIL - 1);
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest("wrong-password")));

        assertEquals(ErrorCode.ACCOUNT_LOCKED.getCode(), ex.getCode());
        assertTrue(capturedSetClause().contains("locked_until"), "达阈值应写入锁定时间");
    }

    @Test
    @DisplayName("账号处于锁定期内时直接拒绝且不校验密码")
    void loginWhileLocked() {
        SysUser user = activeUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest(RAW_PASSWORD)));

        assertEquals(ErrorCode.ACCOUNT_LOCKED.getCode(), ex.getCode());
        verify(sysUserMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("锁定已过期时允许重新登录")
    void loginAfterLockExpired() {
        SysUser user = activeUser();
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        LoginResponse response = authService.login(loginRequest(RAW_PASSWORD));

        assertNotNull(response.getAccessToken());
    }

    @Test
    @DisplayName("账号被管理员锁定或停用时拒绝登录")
    void loginWithUnavailableStatus() {
        SysUser locked = activeUser();
        locked.setStatus("LOCKED");
        when(sysUserMapper.selectOne(any())).thenReturn(locked);
        assertEquals(ErrorCode.ACCOUNT_LOCKED.getCode(),
                assertThrows(BusinessException.class, () -> authService.login(loginRequest(RAW_PASSWORD))).getCode());

        SysUser disabled = activeUser();
        disabled.setStatus("DISABLED");
        when(sysUserMapper.selectOne(any())).thenReturn(disabled);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest(RAW_PASSWORD)));
        assertEquals(ErrorCode.AUTH_FAILED.getCode(), ex.getCode());
        assertEquals("账号已停用", ex.getMessage());

        verify(sysUserMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("开启 MFA 的账号在 TOTP 未实现前明确拒绝，不放行")
    void loginWithMfaEnabled() {
        SysUser user = activeUser();
        user.setMfaEnabled(true);
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginRequest(RAW_PASSWORD)));

        assertEquals(ErrorCode.MFA_REQUIRED.getCode(), ex.getCode());
        verify(sysUserMapper, never()).update(isNull(), any());
    }

    // ------------------------------------------------------------------ 刷新令牌

    @Test
    @DisplayName("刷新令牌有效时重新签发双令牌")
    void refreshSuccess() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());

        LoginResponse response = authService.refresh(jwtUtil.issueRefreshToken(USER_ID));

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals(authProperties.getAccessTokenTtl().toSeconds(), response.getExpiresIn());

        JwtPayload access = jwtUtil.parse(response.getAccessToken());
        assertEquals(USER_ID, access.getUserId().longValue());
        assertEquals(JwtUtil.TYPE_ACCESS, access.getType());

        // 返回的刷新令牌本身可用 —— 说明轮换出来的新令牌是有效的
        assertEquals(JwtUtil.TYPE_REFRESH, jwtUtil.parse(response.getRefreshToken()).getType());
        assertEquals(USER_ID, jwtUtil.parse(response.getRefreshToken()).getUserId().longValue());
    }

    @Test
    @DisplayName("拿访问令牌去刷新时被拒绝")
    void refreshWithAccessToken() {
        String accessToken = jwtUtil.issueAccessToken(USER_ID, "alice", null, List.of(), List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refresh(accessToken));

        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
        verify(sysUserMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("空刷新令牌直接判为无效")
    void refreshWithBlankToken() {
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> authService.refresh("  ")).getCode());
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> authService.refresh(null)).getCode());
    }

    @Test
    @DisplayName("刷新令牌已过期时返回 Token已过期")
    void refreshWithExpiredToken() {
        AuthProperties expired = new AuthProperties();
        expired.setSecret(SECRET);
        expired.setRefreshTokenTtl(Duration.ofSeconds(-1));
        String expiredToken = new JwtUtil(expired).issueRefreshToken(USER_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refresh(expiredToken));

        assertEquals(ErrorCode.TOKEN_EXPIRED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("刷新令牌签名不符时返回 Token无效")
    void refreshWithForeignSignature() {
        AuthProperties other = new AuthProperties();
        other.setSecret("another-secret-key-also-32-bytes-long-xx");
        String foreignToken = new JwtUtil(other).issueRefreshToken(USER_ID);

        assertEquals(ErrorCode.TOKEN_INVALID.getCode(),
                assertThrows(BusinessException.class, () -> authService.refresh(foreignToken)).getCode());
    }

    @Test
    @DisplayName("用户已不存在或已停用时不允许续期")
    void refreshForUnusableUser() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), assertThrows(BusinessException.class,
                () -> authService.refresh(jwtUtil.issueRefreshToken(USER_ID))).getCode());

        SysUser disabled = activeUser();
        disabled.setStatus("DISABLED");
        when(sysUserMapper.selectOne(any())).thenReturn(disabled);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refresh(jwtUtil.issueRefreshToken(USER_ID)));
        assertEquals("账号已停用", ex.getMessage());
    }

    // ------------------------------------------------------------------ 登出吊销

    @Test
    @DisplayName("登出时按 jti 分别吊销访问令牌与刷新令牌")
    void logoutRevokesBothTokens() {
        String accessToken = jwtUtil.issueAccessToken(USER_ID, "alice", null, List.of(), List.of());
        String refreshToken = jwtUtil.issueRefreshToken(USER_ID);
        String accessJti = jwtUtil.parse(accessToken).getJti();
        String refreshJti = jwtUtil.parse(refreshToken).getJti();

        authService.logout(accessToken, refreshToken);

        ArgumentCaptor<String> jtiCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(tokenRevocationService, times(2)).revoke(jtiCaptor.capture(), eq(USER_ID),
                typeCaptor.capture(), any(Instant.class), eq(TokenRevocationService.REASON_LOGOUT));

        assertEquals(List.of(accessJti, refreshJti), jtiCaptor.getAllValues());
        assertEquals(List.of(JwtUtil.TYPE_ACCESS, JwtUtil.TYPE_REFRESH), typeCaptor.getAllValues());
    }

    @Test
    @DisplayName("未携带令牌时登出为幂等空操作，不写吊销表")
    void logoutWithoutToken() {
        authService.logout(null, "   ");

        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    @DisplayName("已过期的令牌在登出时被静默忽略，登出仍然成功")
    void logoutWithExpiredToken() {
        AuthProperties expired = new AuthProperties();
        expired.setSecret(SECRET);
        expired.setAccessTokenTtl(Duration.ofSeconds(-10));
        String expiredToken = new JwtUtil(expired).issueAccessToken(USER_ID, "alice", null, List.of(), List.of());

        authService.logout(expiredToken, null);

        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    @DisplayName("把刷新令牌当访问令牌提交时不会被吊销")
    void logoutIgnoresMismatchedTokenType() {
        authService.logout(jwtUtil.issueRefreshToken(USER_ID), null);

        verifyNoInteractions(tokenRevocationService);
    }

    @Test
    @DisplayName("已被吊销的刷新令牌不能再换取访问令牌")
    void refreshWithRevokedToken() {
        String refreshToken = jwtUtil.issueRefreshToken(USER_ID);
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());
        when(tokenRevocationService.isRevoked(jwtUtil.parse(refreshToken).getJti())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.refresh(refreshToken));

        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    // ------------------------------------------------------------------ 修改密码

    @Test
    @DisplayName("旧密码正确且新密码合规时更新哈希并写一条历史")
    void changePasswordSuccess() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());
        doReturn(emptyHistory()).when(passwordHistoryMapper).selectPage(any(), any());

        authService.changePassword(USER_ID, RAW_PASSWORD, NEW_PASSWORD);

        String sqlSet = capturedSetClause();
        assertTrue(sqlSet.contains("password_hash"), "应更新密码哈希：" + sqlSet);
        assertTrue(sqlSet.contains("password_changed_at"), "应记录改密时间：" + sqlSet);

        PasswordHistory saved = capturedHistory();
        assertEquals(USER_ID, saved.getUserId().longValue());
        assertNotNull(saved.getChangedAt());
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, saved.getPasswordHash()), "历史中应保存新密码的哈希");
    }

    @Test
    @DisplayName("未取得当前用户身份时拒绝改密")
    void changePasswordWithoutIdentity() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(null, RAW_PASSWORD, NEW_PASSWORD));

        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
        verify(sysUserMapper, never()).update(isNull(), any());
        verify(passwordHistoryMapper, never()).insert(any(PasswordHistory.class));
    }

    @Test
    @DisplayName("旧密码错误时拒绝改密")
    void changePasswordWithWrongOldPassword() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(USER_ID, "wrong-old", NEW_PASSWORD));

        assertEquals(ErrorCode.AUTH_FAILED.getCode(), ex.getCode());
        assertEquals("旧密码错误", ex.getMessage());
        verify(sysUserMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("新密码不满足长度或字符类别要求时拒绝")
    void changePasswordWithWeakPassword() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());

        BusinessException tooShort = assertThrows(BusinessException.class,
                () -> authService.changePassword(USER_ID, RAW_PASSWORD, "Ab1"));
        assertEquals(ErrorCode.PARAM_INVALID.getCode(), tooShort.getCode());
        assertEquals("新密码长度不能少于 8 位", tooShort.getMessage());

        // 缺大写
        BusinessException noUpper = assertThrows(BusinessException.class,
                () -> authService.changePassword(USER_ID, RAW_PASSWORD, "abcd1234"));
        assertEquals("新密码必须包含大写字母、小写字母和数字", noUpper.getMessage());

        // 缺数字
        BusinessException noDigit = assertThrows(BusinessException.class,
                () -> authService.changePassword(USER_ID, RAW_PASSWORD, "Abcdefgh"));
        assertEquals("新密码必须包含大写字母、小写字母和数字", noDigit.getMessage());

        verify(sysUserMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("新密码与当前密码相同时拒绝")
    void changePasswordSameAsCurrent() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(USER_ID, RAW_PASSWORD, RAW_PASSWORD));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        assertEquals("新密码不能与当前密码相同", ex.getMessage());
        verify(passwordHistoryMapper, never()).insert(any(PasswordHistory.class));
    }

    @Test
    @DisplayName("新密码命中最近 N 次历史时拒绝")
    void changePasswordReusedFromHistory() {
        SysUser user = activeUser();
        user.setPasswordHash(passwordEncoder.encode("CurrentP@ss1"));
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        PasswordHistory previous = new PasswordHistory();
        previous.setUserId(USER_ID);
        previous.setPasswordHash(passwordEncoder.encode("OldP@ss123"));
        previous.setChangedAt(LocalDateTime.now().minusDays(30));
        doReturn(historyPage(previous)).when(passwordHistoryMapper).selectPage(any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.changePassword(USER_ID, "CurrentP@ss1", "OldP@ss123"));

        assertEquals(ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
        assertEquals("新密码不能与最近 3 次密码相同", ex.getMessage());
        verify(sysUserMapper, never()).update(isNull(), any());
        verify(passwordHistoryMapper, never()).insert(any(PasswordHistory.class));
    }

    @Test
    @DisplayName("刷新令牌时重新解析租户与角色，不沿用旧令牌里的值")
    void refreshReResolvesAuthority() {
        when(sysUserMapper.selectOne(any())).thenReturn(activeUser());
        String refreshToken = jwtUtil.issueRefreshToken(USER_ID);

        when(userAuthorityService.resolve(USER_ID)).thenReturn(authority(1L, "VIEWER"));
        JwtPayload first = jwtUtil.parse(authService.refresh(refreshToken).getAccessToken());
        assertEquals(1L, first.getTenantId().longValue());
        assertEquals(List.of("VIEWER"), first.getRoles());

        // 归属在库中发生变化（换租户 / 换角色）后，续期应拿到新值
        when(userAuthorityService.resolve(USER_ID)).thenReturn(authority(2L, "AUDITOR"));
        JwtPayload second = jwtUtil.parse(authService.refresh(refreshToken).getAccessToken());
        assertEquals(2L, second.getTenantId().longValue());
        assertEquals(List.of("AUDITOR"), second.getRoles());
    }

    // ------------------------------------------------------------------ 辅助

    /** 造一个租户 + 单角色 的归属结果。 */
    private UserAuthority authority(Long tenantId, String roleName) {
        UserAuthority authority = new UserAuthority();
        authority.setTenantId(tenantId);
        authority.setRoleNames(List.of(roleName));
        return authority;
    }

    /** 造一个密码正确、状态正常的用户。 */
    private SysUser activeUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setPasswordHash(passwordHash);
        user.setStatus("ACTIVE");
        user.setMfaEnabled(false);
        user.setLoginFailCount(0);
        return user;
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword(password);
        return request;
    }

    private Page<PasswordHistory> emptyHistory() {
        return historyPage();
    }

    private Page<PasswordHistory> historyPage(PasswordHistory... records) {
        Page<PasswordHistory> page = new Page<>(1, 3);
        page.setRecords(List.of(records));
        return page;
    }

    /** 取出最近一次更新的 SET 片段，用于断言实际写入的列。 */
    private String capturedSetClause() {
        ArgumentCaptor<LambdaUpdateWrapper<SysUser>> captor =
                ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(sysUserMapper, times(1)).update(isNull(), captor.capture());
        return captor.getValue().getSqlSet();
    }

    /** 取出写入的密码历史记录。 */
    private PasswordHistory capturedHistory() {
        ArgumentCaptor<PasswordHistory> captor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(passwordHistoryMapper, times(1)).insert(captor.capture());
        return captor.getValue();
    }
}
