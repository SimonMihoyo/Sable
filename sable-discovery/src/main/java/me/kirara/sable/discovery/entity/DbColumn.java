package me.kirara.sable.discovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 数据字段 db_column */
@TableName("db_column")
public class DbColumn implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tableId;
    private String columnName;
    private Integer ordinalPos;
    private String dataType;
    private Integer charMaxLength;
    private Integer numericPrecision;
    private String columnComment;
    private Boolean isPrimaryKey;
    private Boolean isNullable;
    private String defaultValue;
    /** SPI / PI / IMPORTANT / GENERAL */
    private String sensitivity;
    private String category;
    private Long matchedRuleId;
    /** 是否人工修正 */
    private Boolean manualOverride;
    /** NONE / AT_REST / IN_TRANSIT / BOTH */
    private String encryptionReq;
    private String maskingRule;
    private LocalDateTime scannedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public Integer getOrdinalPos() { return ordinalPos; }
    public void setOrdinalPos(Integer ordinalPos) { this.ordinalPos = ordinalPos; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getCharMaxLength() { return charMaxLength; }
    public void setCharMaxLength(Integer charMaxLength) { this.charMaxLength = charMaxLength; }
    public Integer getNumericPrecision() { return numericPrecision; }
    public void setNumericPrecision(Integer numericPrecision) { this.numericPrecision = numericPrecision; }
    public String getColumnComment() { return columnComment; }
    public void setColumnComment(String columnComment) { this.columnComment = columnComment; }
    public Boolean getIsPrimaryKey() { return isPrimaryKey; }
    public void setIsPrimaryKey(Boolean isPrimaryKey) { this.isPrimaryKey = isPrimaryKey; }
    public Boolean getIsNullable() { return isNullable; }
    public void setIsNullable(Boolean isNullable) { this.isNullable = isNullable; }
    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(Long matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public Boolean getManualOverride() { return manualOverride; }
    public void setManualOverride(Boolean manualOverride) { this.manualOverride = manualOverride; }
    public String getEncryptionReq() { return encryptionReq; }
    public void setEncryptionReq(String encryptionReq) { this.encryptionReq = encryptionReq; }
    public String getMaskingRule() { return maskingRule; }
    public void setMaskingRule(String maskingRule) { this.maskingRule = maskingRule; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
}
