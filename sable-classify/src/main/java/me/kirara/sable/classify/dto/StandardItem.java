package me.kirara.sable.classify.dto;

/** 分类分级标准条目。 */
public class StandardItem {

    private String category;
    private String sensitivity;
    private String description;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
