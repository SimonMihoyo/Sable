package me.kirara.sable.audit.dto;

/** 日志完整性校验结果。 */
public class AuditVerifyResult {

    private boolean valid;
    private long checkedCount;
    private Long brokenAtId;

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public long getCheckedCount() { return checkedCount; }
    public void setCheckedCount(long checkedCount) { this.checkedCount = checkedCount; }
    public Long getBrokenAtId() { return brokenAtId; }
    public void setBrokenAtId(Long brokenAtId) { this.brokenAtId = brokenAtId; }
}
