package me.kirara.sable.system.dto;

import me.kirara.sable.system.entity.SysUser;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户信息响应体 — 用户实体的**脱敏投影**。
 *
 * <p><b>为什么不直接返回 {@link SysUser}：</b>实体是全量映射，包含
 * {@code password_hash}（BCrypt 哈希）与 {@code mfa_secret}（TOTP 密钥）两列凭证。
 * 直接序列化实体等于把凭证发给调用方。这里用**白名单**方式只挑可对外暴露的字段，
 * 新增敏感列时默认不外泄，而不是每次都要记得去加忽略注解。</p>
 *
 * <p>刻意排除的字段及理由：</p>
 * <ul>
 *   <li>{@code passwordHash} / {@code mfaSecret} —— 凭证，绝不能出网；</li>
 *   <li>{@code lastLoginIp} —— 属个人画像信息，当前无接口需要；</li>
 *   <li>{@code loginFailCount} / {@code passwordChangedAt} —— 内部风控状态，对外无意义；</li>
 *   <li>{@code deletedAt} —— 软删除标记，已删除用户不应出现在响应里。</li>
 * </ul>
 *
 * <p>字段形状对齐 `doc/数据合规系统-API接口设计文档.md` 4.9 节的响应样例。</p>
 */
public class UserResponse {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String phone;
    private String avatarUrl;
    /** ACTIVE / LOCKED / DISABLED */
    private String status;
    private Boolean mfaEnabled;
    private LocalDateTime lastLoginAt;
    /** 锁定截止时间，仅 status=LOCKED 时有值，供管理端展示剩余锁定时间。 */
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 角色标识列表（角色的 {@code name}），按<b>当前租户</b>装配。
     *
     * <p>刻意用空列表而非 {@code null} 作默认值：Jackson 的
     * {@code default-property-inclusion} 配的是 {@code non_null}，null 会让字段整个从
     * JSON 里消失，前端就得区分"响应里没有这个键"与"这个键是空数组"两种情形。
     * 空列表会稳定序列化成 {@code []}，前端可以无脑遍历。</p>
     *
     * <p>不在 {@link #from(me.kirara.sable.system.entity.SysUser)} 里赋值 ——
     * 角色归属来自别的表，属调用方装配的内容。</p>
     */
    private List<String> roles = List.of();

    /**
     * 权限标识列表 —— 该用户全部角色 {@code permissions} 列的并集去重，同样采用空列表默认值。
     *
     * <p>当前无权限码字典，值即角色上配置的自由字符串（见 {@code UserAuthority#getPermissions()}）。</p>
     */
    private List<String> permissions = List.of();

    /**
     * 由实体投影出响应对象。
     *
     * @param user 用户实体，可为 null（null 时返回 null，便于调用方直接透传）
     * @return 脱敏后的响应对象
     */
    public static UserResponse from(SysUser user) {
        if (user == null) {
            return null;
        }
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setStatus(user.getStatus());
        response.setMfaEnabled(user.getMfaEnabled());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setLockedUntil(user.getLockedUntil());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    /**
     * 批量投影，用于分页 / 列表场景。
     *
     * @param users 实体列表，可为 null
     * @return 响应列表；入参为 null 时返回空列表
     */
    public static List<UserResponse> fromAll(List<SysUser> users) {
        if (users == null) {
            return List.of();
        }
        return users.stream().map(UserResponse::from).toList();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(Boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles == null ? List.of() : roles; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) {
        this.permissions = permissions == null ? List.of() : permissions;
    }
}
