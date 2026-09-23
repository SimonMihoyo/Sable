package me.kirara.sable.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 审计日志表 audit_log（按月分区） */
@TableName("audit_log")
public class AuditLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String userName;
    /** QUERY / UPDATE / DELETE / EXPORT / SHARE / LOGIN / LOGOUT / CONFIG_CHANGE / DATA_ACCESS */
    private String operation;
    /** 目标数据描述 schema.table.column */
    private String targetDesc;
    /** 涉及数据的最高敏感级别 */
    private String targetLevel;
    private String sourceIp;
    private String requestPath;
    private String requestMethod;
    /** 脱敏后的请求参数摘要 */
    private String operationContext;
    /** SUCCESS / FAILURE / DENIED */
    private String result;
    private String resultMessage;
    private Integer durationMs;
    /** 上一条日志的 hash */
    private String prevHash;
    /** 本条日志 SHA-256 哈希 */
    private String hash;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }
    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }
    public String getOperationContext() { return operationContext; }
    public void setOperationContext(String operationContext) { this.operationContext = operationContext; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getPrevHash() { return prevHash; }
    public void setPrevHash(String prevHash) { this.prevHash = prevHash; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
