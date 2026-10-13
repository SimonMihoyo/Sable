package me.kirara.sable.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 令牌吊销表 public.token_revocation
 *
 * <p>登出或强制下线时按 {@code jti} 写入一行；认证过滤器解析令牌后比对本表，
 * 命中即视为令牌已失效。行本身不可变（只有 {@code revoked_at} 一个时间戳），
 * 故不继承 {@code BaseEntity}；令牌自然过期后由定时任务清理。</p>
 */
@TableName("token_revocation")
public class TokenRevocation {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 被吊销令牌的持有者 */
    private Long userId;
    /** JWT 标准声明 jti —— 令牌唯一标识，库中唯一 */
    private String jti;
    /** access / refresh */
    private String tokenType;
    /** 令牌自身的过期时间 —— 超过该时刻的行已无意义，可清理 */
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    /** 吊销原因，见 {@code TokenRevocationService} 中的常量 */
    private String reason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getJti() { return jti; }
    public void setJti(String jti) { this.jti = jti; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
