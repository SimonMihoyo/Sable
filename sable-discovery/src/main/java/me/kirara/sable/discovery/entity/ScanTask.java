package me.kirara.sable.discovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 扫描任务 scan_task */
@TableName("scan_task")
public class ScanTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Long sourceId;
    /** FULL / INCREMENTAL */
    private String scanType;
    /** MANUAL / SCHEDULED / API */
    private String triggerType;
    private String cronExpression;
    /** PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    private String status;
    private Integer totalSchemas;
    private Integer totalTables;
    private Integer totalColumns;
    private Integer sensitiveFound;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Long createdBy;
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getScanType() { return scanType; }
    public void setScanType(String scanType) { this.scanType = scanType; }
    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalSchemas() { return totalSchemas; }
    public void setTotalSchemas(Integer totalSchemas) { this.totalSchemas = totalSchemas; }
    public Integer getTotalTables() { return totalTables; }
    public void setTotalTables(Integer totalTables) { this.totalTables = totalTables; }
    public Integer getTotalColumns() { return totalColumns; }
    public void setTotalColumns(Integer totalColumns) { this.totalColumns = totalColumns; }
    public Integer getSensitiveFound() { return sensitiveFound; }
    public void setSensitiveFound(Integer sensitiveFound) { this.sensitiveFound = sensitiveFound; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
