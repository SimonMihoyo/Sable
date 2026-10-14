package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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

import java.util.List;
import java.util.Objects;

/**
 * 用户租户 / 角色归属服务实现。
 *
 * <p>归属链路：{@code tenant_user}（按 user_id 查，取 ACTIVE 且未删除的）→ 取 {@code tenant_id}
 * → 经 {@code tenant_user_role} 查出该用户在本租户下被授予的全部 {@code role_id}
 * → 按 id 查未删除的 {@code role} → 取角色 name 列表。</p>
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
        UserAuthority authority = new UserAuthority();
        if (userId == null) {
            return authority;
        }

        List<TenantUser> memberships = tenantUserMapper.selectList(Wrappers.<TenantUser>lambdaQuery()
                .eq(TenantUser::getUserId, userId)
                .eq(TenantUser::getStatus, MEMBERSHIP_ACTIVE)
                .isNull(TenantUser::getDeletedAt)
                .orderByAsc(TenantUser::getId));

        if (memberships.isEmpty()) {
            // 系统级用户（例如平台运维）：没有租户归属，令牌里的 tid 留空
            log.debug("用户无可用租户归属 userId={}", userId);
            return authority;
        }

        TenantUser primary = memberships.get(0);
        authority.setTenantId(primary.getTenantId());

        if (memberships.size() > 1) {
            log.warn("用户属于多个租户，暂按最早加入的一个签发令牌 userId={} 选中租户={} 全部租户={}",
                    userId, primary.getTenantId(),
                    memberships.stream().map(TenantUser::getTenantId).toList());
        }

        authority.setRoleNames(loadRoleNames(primary.getTenantId(), userId));
        return authority;
    }

    /**
     * 取该用户在某租户下被授予的全部角色标识名。
     *
     * <p>已软删除的角色会在结果里被剔除，而不是抛错 —— 用户仍在租户里，只是那个角色没了。
     * 授予记录本身没有软删除列（撤销即物理删除），所以这里不需要过滤。</p>
     */
    private List<String> loadRoleNames(Long tenantId, Long userId) {
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
                        .isNull(Role::getDeletedAt))
                .stream()
                .map(Role::getName)
                .filter(Objects::nonNull)
                .toList();
    }
}
