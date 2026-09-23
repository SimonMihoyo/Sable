package me.kirara.sable.discovery.dto;

/** 定时扫描配置请求体。 */
public class ScheduleRequest {

    private String cronExpression;
    private Boolean enabled;

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
