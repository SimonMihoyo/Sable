package me.kirara.sable.discovery.dto;

/** 发起扫描请求体。 */
public class ScanCreateRequest {

    private String name;
    /** FULL / INCREMENTAL，缺省 FULL。 */
    private String scanType;
    /** MANUAL / SCHEDULED / API，缺省 MANUAL。 */
    private String triggerType;
    private String cronExpression;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
}
