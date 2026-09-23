package me.kirara.sable.alert.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** 新增 / 更新告警规则请求体。 */
public class AlertRuleSaveRequest {

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    /** 规则配置（JSON 字符串）。 */
    private String config;

    /** INFO / WARNING / CRITICAL。 */
    private String severity;

    private List<String> notifyChannels;
    private List<String> notifyTargets;

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
    public List<String> getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(List<String> notifyChannels) { this.notifyChannels = notifyChannels; }
    public List<String> getNotifyTargets() { return notifyTargets; }
    public void setNotifyTargets(List<String> notifyTargets) { this.notifyTargets = notifyTargets; }
}
