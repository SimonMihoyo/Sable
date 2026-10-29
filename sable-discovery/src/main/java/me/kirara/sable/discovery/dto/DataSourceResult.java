package me.kirara.sable.discovery.dto;

import me.kirara.sable.discovery.entity.DataSource;

import java.time.LocalDateTime;

/**
 * 数据源响应投影。
 *
 * <p>接口一律返回本类而不是 {@link DataSource} 实体：实体上带着
 * {@code passwordEnc}（口令密文）、{@code createdBy}、{@code deletedAt} 这些
 * 不该出网的字段。密文本身解不开，但在合规产品里"把凭证相关的字节发到浏览器"
 * 这件事无论能不能利用都不该发生。启动模块还挂了一层 Jackson 兜底（防有人
 * 又直接返回实体），两层各管一段。</p>
 */
public class DataSourceResult {

    private Long id;
    private String name;
    private String type;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String jdbcOptions;
    private String groupName;
    private String description;
    private String status;
    private LocalDateTime lastScanAt;
    private String lastScanStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String scheduleCron;
    private Boolean scheduleEnabled;
    private LocalDateTime scheduleNextRunAt;

    /** 由实体投影出响应对象。 */
    public static DataSourceResult of(DataSource source) {
        if (source == null) {
            return null;
        }
        DataSourceResult result = new DataSourceResult();
        result.id = source.getId();
        result.name = source.getName();
        result.type = source.getType();
        result.host = source.getHost();
        result.port = source.getPort();
        result.databaseName = source.getDatabaseName();
        result.username = source.getUsername();
        result.jdbcOptions = source.getJdbcOptions();
        result.groupName = source.getGroupName();
        result.description = source.getDescription();
        result.status = source.getStatus();
        result.lastScanAt = source.getLastScanAt();
        result.lastScanStatus = source.getLastScanStatus();
        result.createdAt = source.getCreatedAt();
        result.updatedAt = source.getUpdatedAt();
        result.scheduleCron = source.getScheduleCron();
        result.scheduleEnabled = source.getScheduleEnabled();
        result.scheduleNextRunAt = source.getScheduleNextRunAt();
        return result;
    }

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
    public String getJdbcOptions() { return jdbcOptions; }
    public void setJdbcOptions(String jdbcOptions) { this.jdbcOptions = jdbcOptions; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastScanAt() { return lastScanAt; }
    public void setLastScanAt(LocalDateTime lastScanAt) { this.lastScanAt = lastScanAt; }
    public String getLastScanStatus() { return lastScanStatus; }
    public void setLastScanStatus(String lastScanStatus) { this.lastScanStatus = lastScanStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getScheduleCron() { return scheduleCron; }
    public void setScheduleCron(String scheduleCron) { this.scheduleCron = scheduleCron; }
    public Boolean getScheduleEnabled() { return scheduleEnabled; }
    public void setScheduleEnabled(Boolean scheduleEnabled) { this.scheduleEnabled = scheduleEnabled; }
    public LocalDateTime getScheduleNextRunAt() { return scheduleNextRunAt; }
    public void setScheduleNextRunAt(LocalDateTime scheduleNextRunAt) { this.scheduleNextRunAt = scheduleNextRunAt; }
}
