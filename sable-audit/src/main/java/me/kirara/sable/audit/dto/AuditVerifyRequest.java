package me.kirara.sable.audit.dto;

/** 日志完整性校验请求体。 */
public class AuditVerifyRequest {

    private long fromId;
    private long toId;

    public long getFromId() { return fromId; }
    public void setFromId(long fromId) { this.fromId = fromId; }
    public long getToId() { return toId; }
    public void setToId(long toId) { this.toId = toId; }
}
