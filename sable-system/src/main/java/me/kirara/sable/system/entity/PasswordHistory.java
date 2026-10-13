package me.kirara.sable.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 密码历史表 public.password_history
 *
 * <p>只追加、不更新，仅有 {@code changed_at} 一个时间戳列，故不继承 {@code BaseEntity}。</p>
 */
@TableName("password_history")
public class PasswordHistory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** BCrypt 哈希 */
    private String passwordHash;
    private LocalDateTime changedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
