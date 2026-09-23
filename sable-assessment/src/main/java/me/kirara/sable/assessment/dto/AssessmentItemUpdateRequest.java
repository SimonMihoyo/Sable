package me.kirara.sable.assessment.dto;

import java.util.List;

/** 填写检查项评估结果请求体。 */
public class AssessmentItemUpdateRequest {

    /** PENDING / PASSED / PARTIAL / FAILED / NA。 */
    private String status;
    private String evidence;
    private List<String> evidenceFiles;
    private String remark;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public List<String> getEvidenceFiles() { return evidenceFiles; }
    public void setEvidenceFiles(List<String> evidenceFiles) { this.evidenceFiles = evidenceFiles; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
