package me.kirara.sable.alert.dto;

import java.util.List;

/** 批量处理告警请求体。 */
public class BatchHandleRequest {

    private List<Long> eventIds;
    private String status;
    private String handleRemark;

    public List<Long> getEventIds() { return eventIds; }
    public void setEventIds(List<Long> eventIds) { this.eventIds = eventIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHandleRemark() { return handleRemark; }
    public void setHandleRemark(String handleRemark) { this.handleRemark = handleRemark; }
}
