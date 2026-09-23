package me.kirara.sable.classify.dto;

import jakarta.validation.constraints.NotBlank;

/** 新增 / 更新分类分级规则请求体。 */
public class RuleSaveRequest {

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "分类不能为空")
    private String category;

    @NotBlank(message = "敏感级别不能为空")
    private String sensitivity;

    @NotBlank(message = "匹配方式不能为空")
    private String matchType;

    private String pattern;
    private Integer priority;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
