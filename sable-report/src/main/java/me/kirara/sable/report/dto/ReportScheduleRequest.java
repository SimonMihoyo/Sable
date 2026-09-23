package me.kirara.sable.report.dto;

import java.util.List;

/** 配置定期报告请求体。 */
public class ReportScheduleRequest {

    private String name;
    private String type;
    private String format;
    private String cronExpression;
    private List<String> notifyChannels;
    private Boolean enabled;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public List<String> getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(List<String> notifyChannels) { this.notifyChannels = notifyChannels; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
