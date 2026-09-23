package me.kirara.sable.assessment.dto;

import java.time.LocalDate;

/** 分配整改任务请求体。 */
public class RectifyRequest {

    private Boolean hasGap;
    private String gapDescription;
    private String suggestion;
    private Long assigneeId;
    private LocalDate dueDate;
    /** NONE / PENDING / IN_PROGRESS / VERIFIED / DONE。 */
    private String rectifyStatus;

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
}
