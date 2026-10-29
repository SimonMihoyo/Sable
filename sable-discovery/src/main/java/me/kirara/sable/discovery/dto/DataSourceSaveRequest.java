package me.kirara.sable.discovery.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 新增 / 更新数据源请求体。 */
public class DataSourceSaveRequest {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotBlank(message = "数据源类型不能为空")
    @Pattern(regexp = "MYSQL|POSTGRESQL|ORACLE|SQLSERVER|MONGODB|REDIS",
            message = "数据源类型只能是 MYSQL / POSTGRESQL / ORACLE / SQLSERVER / MONGODB / REDIS")
    private String type;

    @NotBlank(message = "主机不能为空")
    private String host;

    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口必须在 1 ~ 65535 之间")
    @Max(value = 65535, message = "端口必须在 1 ~ 65535 之间")
    private Integer port;

    @NotBlank(message = "数据库名不能为空")
    private String databaseName;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 明文口令，服务端加密后写入 {@code password_enc}。
     *
     * <p>新增时必填（库里该列 NOT NULL），由服务层校验 —— 用校验分组会让
     * "新增必填、更新可空"变成两套 DTO，不值得。更新语义是：留空表示不改口令。</p>
     */
    private String password;

    private String jdbcOptions;
    private String groupName;
    private String description;

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
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getJdbcOptions() { return jdbcOptions; }
    public void setJdbcOptions(String jdbcOptions) { this.jdbcOptions = jdbcOptions; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
