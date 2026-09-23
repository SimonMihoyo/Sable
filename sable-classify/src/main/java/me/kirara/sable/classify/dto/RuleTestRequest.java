package me.kirara.sable.classify.dto;

import java.util.List;

/** 规则测试请求体。 */
public class RuleTestRequest {

    private String columnName;
    private String columnComment;
    private List<String> samples;

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getColumnComment() { return columnComment; }
    public void setColumnComment(String columnComment) { this.columnComment = columnComment; }
    public List<String> getSamples() { return samples; }
    public void setSamples(List<String> samples) { this.samples = samples; }
}
