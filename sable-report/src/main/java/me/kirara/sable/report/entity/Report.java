package me.kirara.sable.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;

/** 报告实体 report */
@TableName("report")
public class Report extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    /** ASSESSMENT / ASSET / AUDIT */
    private String type;
    /** PDF / EXCEL */
    private String format;
    /** PENDING / GENERATING / COMPLETED / FAILED */
    private String status;
    private String filePath;
    private Long fileSize;
    /** 生成参数 JSON */
    private String params;
    private Long generatedBy;
    private LocalDateTime generatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public Long getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(Long generatedBy) { this.generatedBy = generatedBy; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
