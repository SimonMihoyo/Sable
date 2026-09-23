package me.kirara.sable.audit.dto;

import java.util.Map;

/** 审计统计结果。 */
public class AuditStatistics {

    private long total;
    private long deniedCount;
    private long failureCount;
    private Map<String, Long> byOperation;
    private Map<String, Long> byLevel;

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getDeniedCount() { return deniedCount; }
    public void setDeniedCount(long deniedCount) { this.deniedCount = deniedCount; }
    public long getFailureCount() { return failureCount; }
    public void setFailureCount(long failureCount) { this.failureCount = failureCount; }
    public Map<String, Long> getByOperation() { return byOperation; }
    public void setByOperation(Map<String, Long> byOperation) { this.byOperation = byOperation; }
    public Map<String, Long> getByLevel() { return byLevel; }
    public void setByLevel(Map<String, Long> byLevel) { this.byLevel = byLevel; }
}
