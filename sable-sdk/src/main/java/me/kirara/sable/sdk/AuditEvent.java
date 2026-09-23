package me.kirara.sable.sdk;

import java.time.LocalDateTime;

/** 审计事件 — 由业务系统采集并上报至 Sablé 审计引擎。 */
public class AuditEvent {

    private Long userId;
    private String userName;
    /** 操作类型，参见 {@link AuditOperations}。 */
    private String operation;
    /** 目标数据描述，如 schema.table.column。 */
    private String targetDesc;
    /** 涉及数据的最高敏感级别：SPI / PI / IMPORTANT / GENERAL。 */
    private String targetLevel;
    private String sourceIp;
    /** SUCCESS / FAILURE / DENIED。 */
    private String result;
    private Integer durationMs;
    /** 操作上下文（脱敏后的参数摘要）。 */
    private String context;
    private LocalDateTime occurredAt;

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
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    /** 建造者，便于链式构造事件。 */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final AuditEvent event = new AuditEvent();

        public Builder userId(Long v) { event.userId = v; return this; }
        public Builder userName(String v) { event.userName = v; return this; }
        public Builder operation(String v) { event.operation = v; return this; }
        public Builder targetDesc(String v) { event.targetDesc = v; return this; }
        public Builder targetLevel(String v) { event.targetLevel = v; return this; }
        public Builder sourceIp(String v) { event.sourceIp = v; return this; }
        public Builder result(String v) { event.result = v; return this; }
        public Builder durationMs(Integer v) { event.durationMs = v; return this; }
        public Builder context(String v) { event.context = v; return this; }
        public Builder occurredAt(LocalDateTime v) { event.occurredAt = v; return this; }

        public AuditEvent build() { return event; }
    }
}
