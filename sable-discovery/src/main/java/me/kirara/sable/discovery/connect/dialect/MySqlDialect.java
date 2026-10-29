package me.kirara.sable.discovery.connect.dialect;

import me.kirara.sable.discovery.entity.DataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * MySQL 方言。
 *
 * <p><b>schema 的处理是这里最关键的一点：</b>MySQL 没有 PostgreSQL 意义上的 schema，
 * 库（database）就是 JDBC 的 catalog。{@code DatabaseMetaData.getSchemas()} 在
 * Connector/J 上拿不到"每个库一行"的结果，直接套用 PostgreSQL 的枚举方式会得到
 * "扫描成功但 0 个 schema"—— 所以这里显式返回配置里的 {@code databaseName}，
 * 并把它作为 catalog 传给元数据查询。</p>
 *
 * <p><b>注释为什么要额外开参数：</b>Connector/J 默认从 {@code SHOW} 语句取元数据，
 * 表/字段的 REMARKS 是空的；把 {@code useInformationSchema=true} 打开后才从
 * {@code INFORMATION_SCHEMA} 读，注释才会出现。这是"看起来扫到了、但资产清单里没有注释"
 * 的典型原因。</p>
 *
 * <p>类型名：MySQL 驱动返回大写的 {@code INT} / {@code VARCHAR} / {@code DATETIME}，
 * 与 PostgreSQL 的内部名（{@code int4}…）不同，方言层统一成大写展示。</p>
 */
@Component
public class MySqlDialect implements JdbcDialect {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

    @Override
    public String type() {
        return "MYSQL";
    }

    @Override
    public String buildUrl(DataSource source) {
        return "jdbc:mysql://" + source.getHost() + ":" + source.getPort() + "/" + source.getDatabaseName()
                + DialectSupport.appendOptions("?", source.getJdbcOptions());
    }

    @Override
    public Properties connectionProperties(DataSource source, String password) {
        Properties properties = DialectSupport.credentials(source, password);
        properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MILLIS));
        properties.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MILLIS));
        // 没有它，表注释 / 字段注释一律为空
        properties.setProperty("useInformationSchema", "true");
        return properties;
    }

    @Override
    public List<String> schemas(Connection connection, DataSource source) {
        // 一个数据源只扫它配置的那个库；同实例的其他库请另建数据源（最小权限）
        return List.of(source.getDatabaseName());
    }

    @Override
    public String catalog(DataSource source, String schema) {
        // MySQL：catalog 就是库名
        return source.getDatabaseName();
    }

    @Override
    public String schemaPattern(DataSource source, String schema) {
        // 库名已经通过 catalog 指定；schemaPattern 传 null 才不会变成 "库.库" 的双重限定
        return null;
    }

    @Override
    public String normalizeTypeName(String rawType) {
        return rawType == null ? null : rawType.trim().toUpperCase(Locale.ROOT);
    }
}
