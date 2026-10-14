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

    /**
     * 权限标识列表 —— 该用户全部角色 {@code permissions} 列的<b>并集去重</b>。
     *
     * <p>权限装配与角色装配放在同一次查询里完成：两者都依赖"这个人在本租户持有哪些角色"，
     * 分开查会重复往返数据库。</p>
     *
     * <p><b>当前无权限码字典</b>：{@code role.permissions} 里是自由字符串
     * （见 {@code V1__init_schema.sql} 的注释），这里只做聚合、不做校验。
     * 待接口级鉴权落地并定出权限码字典后，校验才加得进来。</p>
     */
    private List<String> permissions = List.of();

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

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? List.of() : permissions;
    }
}
