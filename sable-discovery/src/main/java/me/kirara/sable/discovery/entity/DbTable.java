package me.kirara.sable.discovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 数据表 db_table */
@TableName("db_table")
public class DbTable implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long schemaId;
    private String tableName;
    private String tableComment;
    /** TABLE / VIEW */
    private String tableType;
    private Long rowCount;
    private Long sizeBytes;
    private Integer columnCount;
    private LocalDateTime scannedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSchemaId() { return schemaId; }
    public void setSchemaId(Long schemaId) { this.schemaId = schemaId; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getTableComment() { return tableComment; }
    public void setTableComment(String tableComment) { this.tableComment = tableComment; }
    public String getTableType() { return tableType; }
    public void setTableType(String tableType) { this.tableType = tableType; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
}
