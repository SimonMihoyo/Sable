package me.kirara.sable.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;

/** 系统配置表 public.sys_config */
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String configGroup;
    private String configKey;
    private String configValue;
    /** STRING / NUMBER / BOOLEAN / JSON */
    private String valueType;
    private String description;
    private Boolean isEncrypted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConfigGroup() { return configGroup; }
    public void setConfigGroup(String configGroup) { this.configGroup = configGroup; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getIsEncrypted() { return isEncrypted; }
    public void setIsEncrypted(Boolean isEncrypted) { this.isEncrypted = isEncrypted; }
}
