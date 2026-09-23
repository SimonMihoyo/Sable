package me.kirara.sable.discovery.dto;

/** 人工修正字段分级请求体。 */
public class ColumnSensitivityRequest {

    /** SPI / PI / IMPORTANT / GENERAL。 */
    private String sensitivity;
    private String category;
    private String maskingRule;
    private String encryptionReq;

    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getMaskingRule() { return maskingRule; }
    public void setMaskingRule(String maskingRule) { this.maskingRule = maskingRule; }
    public String getEncryptionReq() { return encryptionReq; }
    public void setEncryptionReq(String encryptionReq) { this.encryptionReq = encryptionReq; }
}
