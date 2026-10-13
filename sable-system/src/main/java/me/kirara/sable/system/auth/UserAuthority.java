package me.kirara.sable.system.auth;

import java.util.List;

/**
 * 用户的租户与角色归属 —— 签发令牌时写进声明的原料。
 *
 * <p>与 {@link JwtPayload} 的区别：{@code JwtPayload} 是"把令牌解开后读到的东西"，
 * 本类则是"签发令牌前查库查到的东西"，方向相反。</p>
 */
public class UserAuthority {

    /** 所属租户 ID，为空表示该用户没有可用租户（系统级用户）。 */
    private Long tenantId;

    /** 角色标识列表（角色的 {@code name}，如 SUPER_ADMIN）；无角色时为空列表而非 null。 */
    private List<String> roleNames = List.of();

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public void setRoleNames(List<String> roleNames) {
        this.roleNames = roleNames == null ? List.of() : roleNames;
    }
}
