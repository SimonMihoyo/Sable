package me.kirara.sable.audit.dto;

import me.kirara.sable.common.page.PageQuery;
import java.time.LocalDateTime;

/** 审计日志查询条件。 */
public class AuditLogQuery extends PageQuery {

    private Long userId;
    private String userName;
    private String operation;
    private String targetLevel;
    private String result;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTargetLevel() { return targetLevel; }
    public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
