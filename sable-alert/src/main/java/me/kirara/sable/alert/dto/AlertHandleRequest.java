package me.kirara.sable.alert.dto;

/** 处理告警事件请求体。 */
public class AlertHandleRequest {

    /** ACKNOWLEDGED / INVESTIGATING / RESOLVED / CLOSED。 */
    private String status;
    private String handleRemark;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
}
