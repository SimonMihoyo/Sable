package me.kirara.sable.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;

/** 定期报告配置 report_schedule */
@TableName("report_schedule")
public class ReportSchedule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String type;
    private String format;
    private String cronExpression;
    /** 通知渠道 JSON */
    private String notifyChannels;
    private Boolean isEnabled;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(String notifyChannels) { this.notifyChannels = notifyChannels; }
    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }
}
