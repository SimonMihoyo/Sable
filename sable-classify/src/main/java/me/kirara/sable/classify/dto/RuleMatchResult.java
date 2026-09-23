package me.kirara.sable.classify.dto;

/** 规则匹配结果。 */
public class RuleMatchResult {

    private Long ruleId;
    private String ruleName;
    private String category;
    private String sensitivity;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
}
