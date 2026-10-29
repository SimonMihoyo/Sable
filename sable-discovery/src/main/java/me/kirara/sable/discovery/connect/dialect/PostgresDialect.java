package me.kirara.sable.discovery.connect.dialect;

import me.kirara.sable.discovery.entity.DataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * PostgreSQL 方言。
 *
 * <p>schema 是真实的一层（{@code public}、{@code tenant_1}…），扫描时枚举
 * {@code DatabaseMetaData.getSchemas()}，catalog 传 {@code null}（表示当前库）。
 * 驱动返回的类型名是内部名（{@code int4}、{@code timestamptz}…），这里做一次展示归一。</p>
 */
@Component
public class PostgresDialect implements JdbcDialect {

    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    private static final int SOCKET_TIMEOUT_SECONDS = 5;

    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
            Map.entry("int2", "SMALLINT"),
            Map.entry("int4", "INTEGER"),
            Map.entry("int8", "BIGINT"),
            Map.entry("float4", "REAL"),
            Map.entry("float8", "DOUBLE PRECISION"),
            Map.entry("bool", "BOOLEAN"),
            Map.entry("bpchar", "CHAR"),
            Map.entry("timestamptz", "TIMESTAMP WITH TIME ZONE"),
            Map.entry("timetz", "TIME WITH TIME ZONE"));

    @Override
    public String type() {
        return "POSTGRESQL";
    }

    @Override
    public String buildUrl(DataSource source) {
        return "jdbc:postgresql://" + source.getHost() + ":" + source.getPort() + "/" + source.getDatabaseName()
                + DialectSupport.appendOptions("?", source.getJdbcOptions());
    }

    @Override
    public Properties connectionProperties(DataSource source, String password) {
        Properties properties = DialectSupport.credentials(source, password);
        properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        properties.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_SECONDS));
        properties.setProperty("loginTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
        return properties;
    }

    @Override
    public List<String> schemas(Connection connection, DataSource source) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        List<String> schemas = new ArrayList<>();
        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                // pg_catalog / information_schema / pg_toast / pg_temp_* —— 不是租户资产
                if (schema != null && !schema.startsWith("pg_") && !"information_schema".equals(schema)) {
                    schemas.add(schema);
                }
            }
        }
        return schemas;
    }

    @Override
    public String catalog(DataSource source, String schema) {
        // null = 当前库。PostgreSQL 的 catalog 就是数据库名，指定当前库没有额外收益
        return null;
    }

    @Override
    public String schemaPattern(DataSource source, String schema) {
        return schema;
    }

    @Override
    public String normalizeTypeName(String rawType) {
        if (rawType == null) {
            return null;
        }
        String lower = rawType.trim().toLowerCase(Locale.ROOT);
        return TYPE_ALIASES.getOrDefault(lower, rawType.trim().toUpperCase(Locale.ROOT));
    }
}
