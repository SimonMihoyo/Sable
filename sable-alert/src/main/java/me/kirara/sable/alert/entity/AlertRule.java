package me.kirara.sable.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;

/** 告警规则表 alert_rule */
@TableName("alert_rule")
public class AlertRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    /** THRESHOLD / TIME_BASED / FREQUENCY / PATTERN */
    private String ruleType;
    /** 规则配置 JSONB */
    private String config;
    /** INFO / WARNING / CRITICAL */
    private String severity;
    /** 通知渠道 JSONB */
    private String notifyChannels;
    /** 通知目标 JSONB */
    private String notifyTargets;
    private Boolean isEnabled;
    private Long createdBy;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(String notifyChannels) { this.notifyChannels = notifyChannels; }
    public String getNotifyTargets() { return notifyTargets; }
    public void setNotifyTargets(String notifyTargets) { this.notifyTargets = notifyTargets; }
    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
