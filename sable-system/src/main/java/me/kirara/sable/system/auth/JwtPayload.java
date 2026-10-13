package me.kirara.sable.system.auth;

import java.time.Instant;
import java.util.List;

/**
 * 令牌解析结果 — 从 JWT 载荷中提取的业务字段。
 */
public class JwtPayload {

    /**
     * 令牌唯一标识（JWT 标准声明 {@code jti}）。
     *
     * <p>用于令牌吊销：登出时按 jti 记入吊销表，后续请求命中即视为无效。
     * 不能退而求其次用 {@code iat} 做标识 —— iat 只有秒级精度，
     * 同一秒内签发的两枚令牌 iat 完全相同，无法区分。</p>
     */
    private String jti;

    /** 用户 ID。 */
    private Long userId;

    /** 用户名。 */
    private String username;

    /** 所属租户 ID，可为空（无租户归属的系统级用户）。 */
    private Long tenantId;

    /** 角色标识列表（角色 name，如 SUPER_ADMIN）。 */
    private List<String> roles;

    /** 权限标识列表。 */
    private List<String> permissions;

    /** 令牌类型，见 {@link JwtUtil#TYPE_ACCESS} / {@link JwtUtil#TYPE_REFRESH}。 */
    private String type;

    /** 过期时间。 */
    private Instant expiresAt;

    public String getJti() {
        return jti;
    }

    public void setJti(String jti) {
        this.jti = jti;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
