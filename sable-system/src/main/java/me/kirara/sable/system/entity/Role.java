package me.kirara.sable.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;

/** 角色表 public.role */
@TableName("role")
public class Role extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** SUPER_ADMIN / COMPLIANCE_OFFICER / AUDITOR / DATA_STEWARD / VIEWER */
    private String name;
    private String displayName;
    private String description;
    /** 系统预置角色不可删除 */
    private Boolean isSystem;
    /** 权限列表（JSONB，暂以字符串承载） */
    private String permissions;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
