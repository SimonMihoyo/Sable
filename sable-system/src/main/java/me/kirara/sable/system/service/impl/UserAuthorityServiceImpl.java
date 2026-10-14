package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import me.kirara.sable.common.util.JsonArrays;
import me.kirara.sable.system.auth.UserAuthority;
import me.kirara.sable.system.entity.Role;
import me.kirara.sable.system.entity.TenantUser;
import me.kirara.sable.system.entity.TenantUserRole;
import me.kirara.sable.system.mapper.RoleMapper;
import me.kirara.sable.system.mapper.TenantUserMapper;
import me.kirara.sable.system.mapper.TenantUserRoleMapper;
import me.kirara.sable.system.service.UserAuthorityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户租户 / 角色归属服务实现。
 *
 * <p>归属链路：{@code tenant_user}（按 user_id 查，取 ACTIVE 且未删除的）→ 取 {@code tenant_id}
 * → 经 {@code tenant_user_role} 查出该用户在本租户下被授予的全部 {@code role_id}
 * → 按 id 查未删除的 {@code role} → 取角色 name 列表，并把各角色的 {@code permissions}
 * （JSONB）合并去重成权限列表。</p>
 *
 * <p><b>两条入口，差别只在"租户从哪来"：</b>{@link #resolve(Long)} 用于<b>签发令牌</b>
 * （登录接口没有租户参数，只能按"最早加入"推定）；{@link #resolveInTenant(Long, Long)}
 * 用于<b>已登录请求</b>（租户已由令牌的 {@code tid} 或 {@code X-Tenant-Id} 确定）。</p>
 *
 * <p><b>多角色：</b>一个用户在同一租户下可以有多个角色，令牌里的 {@code roles} 声明是列表，
 * 因此这里返回的是<b>全部</b>有效角色名，而不是取其中一个。</p>
 *
 * <p><b>多租户成员的处理（当前为过渡策略）：</b>登录接口没有租户参数，
 * 因此当用户同时属于多个租户时，取 id 最小（最早加入）的那一个作为默认租户，
 * 并打 WARN 日志。真正的"登录时选租户 / 中途切换租户"需要 API 层面的设计，
 * 届时只需替换本类的选取策略，令牌形状不变。</p>
 */
@Service
public class UserAuthorityServiceImpl implements UserAuthorityService {

    private static final Logger log = LoggerFactory.getLogger(UserAuthorityServiceImpl.class);

    /** tenant_user 状态：正常。 */
    private static final String MEMBERSHIP_ACTIVE = "ACTIVE";

    private final TenantUserMapper tenantUserMapper;
    private final TenantUserRoleMapper tenantUserRoleMapper;
    private final RoleMapper roleMapper;

    public UserAuthorityServiceImpl(TenantUserMapper tenantUserMapper,
                                    TenantUserRoleMapper tenantUserRoleMapper,
                                    RoleMapper roleMapper) {
        this.tenantUserMapper = tenantUserMapper;
        this.tenantUserRoleMapper = tenantUserRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public UserAuthority resolve(Long userId) {
        if (userId == null) {
            return new UserAuthority();
        }

        List<TenantUser> memberships = tenantUserMapper.selectList(Wrappers.<TenantUser>lambdaQuery()
                .eq(TenantUser::getUserId, userId)
                .eq(TenantUser::getStatus, MEMBERSHIP_ACTIVE)
                .isNull(TenantUser::getDeletedAt)
                .orderByAsc(TenantUser::getId));

        if (memberships.isEmpty()) {
            // 系统级用户（例如平台运维）：没有租户归属，令牌里的 tid 留空
            log.debug("用户无可用租户归属 userId={}", userId);
            return new UserAuthority();
        }

        TenantUser primary = memberships.get(0);
        if (memberships.size() > 1) {
            log.warn("用户属于多个租户，暂按最早加入的一个签发令牌 userId={} 选中租户={} 全部租户={}",
                    userId, primary.getTenantId(),
                    memberships.stream().map(TenantUser::getTenantId).toList());
        }
        return fillAuthority(primary.getTenantId(), userId);
    }

    @Override
    public UserAuthority resolveInTenant(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            // 无租户归属是"系统级用户"的正常形态，不是错误：返回空归属即可
            return new UserAuthority();
        }
        return fillAuthority(tenantId, userId);
    }

    /**
     * 按已确定的租户装配归属 —— 角色标识与权限标识在同一次查询里取齐。
     *
     * <p>两者都依赖"这个人在本租户持有哪些角色"，拆成两次会多一组往返。</p>
     *
     * @return 归属结果，{@code tenantId} 必为入参值
     */
    private UserAuthority fillAuthority(Long tenantId, Long userId) {
        UserAuthority authority = new UserAuthority();
        authority.setTenantId(tenantId);

        List<Role> roles = loadRoles(tenantId, userId);
        authority.setRoleNames(roles.stream().map(Role::getName).filter(Objects::nonNull).toList());
        authority.setPermissions(mergePermissions(roles, tenantId));
        return authority;
    }

    /**
     * 取该用户在某租户下被授予的全部角色。
     *
     * <p>已软删除的角色会在结果里被剔除，而不是抛错 —— 用户仍在租户里，只是那个角色没了。
     * 授予记录本身没有软删除列（撤销即物理删除），所以这里不需要过滤。</p>
     *
     * <p>返回<b>实体</b>而非仅角色名：调用方还要读 {@code permissions} 列，
     * 只返回名字就得再查一次。</p>
     */
    private List<Role> loadRoles(Long tenantId, Long userId) {
        List<TenantUserRole> grants = tenantUserRoleMapper.selectList(Wrappers.<TenantUserRole>lambdaQuery()
                .eq(TenantUserRole::getTenantId, tenantId)
                .eq(TenantUserRole::getUserId, userId));
        if (grants.isEmpty()) {
            log.debug("用户在本租户下没有任何角色授予 tenantId={} userId={}", tenantId, userId);
            return List.of();
        }

        List<Long> roleIds = grants.stream().map(TenantUserRole::getRoleId).distinct().toList();
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                .in(Role::getId, roleIds)
                .isNull(Role::getDeletedAt));
    }

    /**
     * 把各角色的 {@code permissions} 合并为去重后的列表。
     *
     * <p>该列是 JSONB，而 Java 侧映射成裸 {@code String}（无 TypeHandler），
     * 因此解析要经 {@link JsonArrays#parseStringArray(String)} —— 库模块取不到 Jackson，
     * 不能指望 {@code ObjectMapper}。</p>
     *
     * <p>用 {@link LinkedHashSet} 保序去重：同一权限被多个角色重复授予时只留一份，
     * 且顺序稳定，便于日志比对与单测断言。</p>
     */
    private List<String> mergePermissions(List<Role> roles, Long tenantId) {
        Set<String> merged = new LinkedHashSet<>();
        for (Role role : roles) {
            String raw = role.getPermissions();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            List<String> parsed = JsonArrays.parseStringArray(raw);
            if (parsed.isEmpty() && !"[]".equals(raw.strip())) {
                // 非空、但又不是字符串数组形态（例如被人工写成了对象）：
                // 按"无权限"处理并留痕，不让一条坏配置把整个请求打成 500
                log.warn("角色的 permissions 不是合法字符串数组，按空权限处理 roleId={} tenantId={} 原值={}",
                        role.getId(), tenantId, raw);
            }
            merged.addAll(parsed);
        }
        return List.copyOf(merged);
    }
}
