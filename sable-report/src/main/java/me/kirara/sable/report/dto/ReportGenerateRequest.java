package me.kirara.sable.report.dto;

/** 生成报告请求体。 */
public class ReportGenerateRequest {

    private String name;
    /** PDF / EXCEL，缺省 PDF。 */
    private String format;
    /** 生成参数（JSON 字符串）。 */
    private String params;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
}
