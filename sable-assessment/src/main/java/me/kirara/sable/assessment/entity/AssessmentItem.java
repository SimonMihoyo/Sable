package me.kirara.sable.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalDate;

/** 评估检查项表 assessment_item */
@TableName("assessment_item")
public class AssessmentItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assessmentId;
    private String category;
    private String refLaw;
    private String refArticle;
    private String content;
    /** AUTO / MANUAL / SEMI_AUTO */
    private String checkType;
    private Integer weight;
    /** PENDING / PASSED / PARTIAL / FAILED / NA */
    private String status;
    private String evidence;
    private String evidenceFiles;
    private String remark;
    private Boolean hasGap;
    private String gapDescription;
    private String suggestion;
    private Long assigneeId;
    private LocalDate dueDate;
    /** NONE / PENDING / IN_PROGRESS / VERIFIED / DONE */
    private String rectifyStatus;
    private String autoResult;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRefLaw() { return refLaw; }
    public void setRefLaw(String refLaw) { this.refLaw = refLaw; }
    public String getRefArticle() { return refArticle; }
    public void setRefArticle(String refArticle) { this.refArticle = refArticle; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCheckType() { return checkType; }
    public void setCheckType(String checkType) { this.checkType = checkType; }
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getEvidenceFiles() { return evidenceFiles; }
    public void setEvidenceFiles(String evidenceFiles) { this.evidenceFiles = evidenceFiles; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getHasGap() { return hasGap; }
    public void setHasGap(Boolean hasGap) { this.hasGap = hasGap; }
    public String getGapDescription() { return gapDescription; }
    public void setGapDescription(String gapDescription) { this.gapDescription = gapDescription; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getRectifyStatus() { return rectifyStatus; }
    public void setRectifyStatus(String rectifyStatus) { this.rectifyStatus = rectifyStatus; }
    public String getAutoResult() { return autoResult; }
    public void setAutoResult(String autoResult) { this.autoResult = autoResult; }
}
