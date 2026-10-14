package me.kirara.sable.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;

/**
 * 租户用户关联表 public.tenant_user
 *
 * <p>只描述「某用户是不是某租户的成员」—— 部门与状态挂在本表。角色**不在这里**：
 * 同一用户在同一租户下可持有多个角色，故拆到 {@link TenantUserRole}，
 * 否则部门与状态会随角色数重复存储。</p>
 */
@TableName("tenant_user")
public class TenantUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    /** 数据范围权限维度 */
    private String department;
    /** ACTIVE / DISABLED */
    private String status;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
