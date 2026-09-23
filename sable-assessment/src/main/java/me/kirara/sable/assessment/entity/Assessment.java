package me.kirara.sable.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/** 合规评估表 assessment */
@TableName("assessment")
public class Assessment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    /** PIPL / DSL / CUSTOM */
    private String standardType;
    private String standardVersion;
    private String description;
    /** DRAFT / IN_PROGRESS / COMPLETED / ARCHIVED */
    private String status;
    private Integer totalItems;
    private Integer passedItems;
    private Integer partialItems;
    private Integer failedItems;
    private Integer naItems;
    private BigDecimal score;
    /** A / B / C / D */
    private String grade;
    private Long assessorId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStandardType() { return standardType; }
    public void setStandardType(String standardType) { this.standardType = standardType; }
    public String getStandardVersion() { return standardVersion; }
    public void setStandardVersion(String standardVersion) { this.standardVersion = standardVersion; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalItems() { return totalItems; }
    public void setTotalItems(Integer totalItems) { this.totalItems = totalItems; }
    public Integer getPassedItems() { return passedItems; }
    public void setPassedItems(Integer passedItems) { this.passedItems = passedItems; }
    public Integer getPartialItems() { return partialItems; }
    public void setPartialItems(Integer partialItems) { this.partialItems = partialItems; }
    public Integer getFailedItems() { return failedItems; }
    public void setFailedItems(Integer failedItems) { this.failedItems = failedItems; }
    public Integer getNaItems() { return naItems; }
    public void setNaItems(Integer naItems) { this.naItems = naItems; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Long getAssessorId() { return assessorId; }
    public void setAssessorId(Long assessorId) { this.assessorId = assessorId; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
