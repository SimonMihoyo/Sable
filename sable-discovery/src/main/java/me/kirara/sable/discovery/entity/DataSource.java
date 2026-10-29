package me.kirara.sable.discovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import me.kirara.sable.common.entity.BaseEntity;
import java.time.LocalDateTime;

/** 数据源表 data_source */
@TableName("data_source")
public class DataSource extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    /** MYSQL / POSTGRESQL / ORACLE / SQLSERVER / MONGODB / REDIS */
    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    /** AES-256-GCM 密文（应用侧加密，密钥由启动模块注入，见 DataSourceCipher） */
    private byte[] passwordEnc;
    private String jdbcOptions;
    private String groupName;
    /**
     * 分组排序权重：越小越靠前。
     *
     * <p>排序挂在每行上而不是单独的分组表上 —— 分组目前是 {@code group_name} 的派生概念，
     * 没有独立生命周期。同一分组的所有行必须保持同值，由服务层维护；读取时按
     * {@code MIN(group_sort)} 排序，容忍个别行被手工改坏。本字段不对外暴露。</p>
     */
    private Integer groupSort;
    private String description;
    /** ACTIVE / INACTIVE / ERROR / TESTING */
    private String status;
    private LocalDateTime lastScanAt;
    /** SUCCESS / PARTIAL / FAILED */
    private String lastScanStatus;
    private String scanErrorMsg;
    private Long createdBy;
    private LocalDateTime deletedAt;
    /** 定时扫描 cron（6 段：秒 分 时 日 月 周）；未配置时为空 */
    private String scheduleCron;
    /** 是否启用定时扫描 */
    private Boolean scheduleEnabled;
    /** 下次执行时间；关闭时为空，每次触发后推进 */
    private LocalDateTime scheduleNextRunAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public byte[] getPasswordEnc() { return passwordEnc; }
    public void setPasswordEnc(byte[] passwordEnc) { this.passwordEnc = passwordEnc; }
    public String getJdbcOptions() { return jdbcOptions; }
    public void setJdbcOptions(String jdbcOptions) { this.jdbcOptions = jdbcOptions; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Integer getGroupSort() { return groupSort; }
    public void setGroupSort(Integer groupSort) { this.groupSort = groupSort; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastScanAt() { return lastScanAt; }
    public void setLastScanAt(LocalDateTime lastScanAt) { this.lastScanAt = lastScanAt; }
    public String getLastScanStatus() { return lastScanStatus; }
    public void setLastScanStatus(String lastScanStatus) { this.lastScanStatus = lastScanStatus; }
    public String getScanErrorMsg() { return scanErrorMsg; }
    public void setScanErrorMsg(String scanErrorMsg) { this.scanErrorMsg = scanErrorMsg; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public Boolean getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public LocalDateTime getScheduleNextRunAt() { return scheduleNextRunAt; }
    public void setScheduleNextRunAt(LocalDateTime scheduleNextRunAt) { this.scheduleNextRunAt = scheduleNextRunAt; }
}
