package me.kirara.sable.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 告警事件表 alert_event */
@TableName("alert_event")
public class AlertEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;
    private String ruleName;
    private String severity;
    private Long triggerUserId;
    private String triggerUser;
    /** 触发详情 JSONB */
    private String triggerDetail;
    /** PENDING / ACKNOWLEDGED / INVESTIGATING / RESOLVED / CLOSED */
    private String status;
    private Long handlerId;
    private String handleRemark;
    private LocalDateTime handledAt;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Long getTriggerUserId() { return triggerUserId; }
    public void setTriggerUserId(Long triggerUserId) { this.triggerUserId = triggerUserId; }
    public String getTriggerUser() { return triggerUser; }
    public void setTriggerUser(String triggerUser) { this.triggerUser = triggerUser; }
    public String getTriggerDetail() { return triggerDetail; }
    public void setTriggerDetail(String triggerDetail) { this.triggerDetail = triggerDetail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
