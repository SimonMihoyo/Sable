package me.kirara.sable.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;

/** 租户表 public.tenant */
@TableName("tenant")
public class Tenant extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 租户编码，用于生成 schema 名 */
    private String code;
    /** BASIC / PROFESSIONAL / ENTERPRISE */
    private String planType;
    /** ACTIVE / SUSPENDED / EXPIRED */
    private String status;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    /** 租户级配置（JSONB，暂以字符串承载） */
    private String config;
    private LocalDateTime expiredAt;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
