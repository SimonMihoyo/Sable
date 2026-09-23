package me.kirara.sable.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** SDK 审计事件上报请求体 — POST /audit/events */
public class AuditEventRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    private String userName;

    @NotBlank(message = "operation 不能为空")
    private String operation;

    @NotBlank(message = "targetDesc 不能为空")
    private String targetDesc;

    private String targetLevel;
    private String sourceIp;
    private String result;
    private Integer durationMs;
    private String context;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTargetDesc() { return targetDesc; }
    public void setTargetDesc(String targetDesc) { this.targetDesc = targetDesc; }
    public String getTargetLevel() { return targetLevel; }
    public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
}
