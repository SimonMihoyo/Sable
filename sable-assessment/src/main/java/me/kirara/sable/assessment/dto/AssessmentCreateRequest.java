package me.kirara.sable.assessment.dto;

import jakarta.validation.constraints.NotBlank;

/** 创建评估任务请求体。 */
public class AssessmentCreateRequest {

    @NotBlank(message = "评估名称不能为空")
    private String name;

    @NotBlank(message = "标准类型不能为空")
    private String standardType;

    @NotBlank(message = "标准版本不能为空")
    private String standardVersion;

    private String description;
    private Long assessorId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStandardType() { return standardType; }
    public void setStandardType(String standardType) { this.standardType = standardType; }
    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getAssessorId() { return assessorId; }
    public void setAssessorId(Long assessorId) { this.assessorId = assessorId; }
}
