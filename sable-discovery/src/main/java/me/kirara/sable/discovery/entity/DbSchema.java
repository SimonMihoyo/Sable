package me.kirara.sable.discovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 数据库 Schema 表 db_schema */
@TableName("db_schema")
public class DbSchema implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sourceId;
    private String schemaName;
    private String comment;
    private Integer tableCount;
    private LocalDateTime scannedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Integer getTableCount() { return tableCount; }
    public void setTableCount(Integer tableCount) { this.tableCount = tableCount; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
}
