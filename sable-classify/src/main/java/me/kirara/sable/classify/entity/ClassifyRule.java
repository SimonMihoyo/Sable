package me.kirara.sable.classify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;

/** 分类分级规则表 classify_rule */
@TableName("classify_rule")
public class ClassifyRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private String category;
    /** SPI / PI / IMPORTANT / GENERAL */
    private String sensitivity;
    /** REGEX / KEYWORD / SAMPLE / COMPOSITE */
    private String matchType;
    private String pattern;
    private Integer priority;
    private Boolean isBuiltin;
    /** ACTIVE / DISABLED */
    private String status;
    private Long createdBy;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSensitivity() { return sensitivity; }
    public void setSensitivity(String sensitivity) { this.sensitivity = sensitivity; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getIsBuiltin() { return isBuiltin; }
    public void setIsBuiltin(Boolean isBuiltin) { this.isBuiltin = isBuiltin; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
