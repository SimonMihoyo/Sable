package me.kirara.sable.assessment.dto;

/** 评估标准检查项模板。 */
public class StandardItemDto {

    private String category;
    private String refLaw;
    private String refArticle;
    private String content;
    private String checkType;
    private Integer weight;

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
}
