package me.kirara.sable.discovery.connect.dialect;

import me.kirara.sable.discovery.entity.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方言层测试 —— 覆盖"各库不一样的那几处"：schema 语义、catalog 参数、类型名归一。
 *
 * <p>MySQL 的 {@code schemas()} 是纯计算（不碰连接），可以直接单测；
 * 真正连接后的元数据行为由 {@code MySqlMetadataScanIntegrationTest}（Testcontainers）覆盖。</p>
 */
class JdbcDialectTest {

    private static DataSource source(String type, String database) {
        DataSource source = new DataSource();
        source.setType(type);
        source.setHost("db.internal");
        source.setPort(3306);
        source.setDatabaseName(database);
        source.setUsername("readonly");
        source.setJdbcOptions(null);
        return source;
    }

    @Test
    @DisplayName("MySQL 没有 schema：只返回配置里的库名，并把它当 catalog")
    void mySqlTreatsDatabaseAsSchema() {
        MySqlDialect dialect = new MySqlDialect();
        assertEquals(List.of("crm_demo"), dialect.schemas(null, source("MYSQL", "crm_demo")));
        assertEquals("crm_demo", dialect.catalog(source("MYSQL", "crm_demo"), "crm_demo"));
        assertNull(dialect.schemaPattern(source("MYSQL", "crm_demo"), "crm_demo"));
    }

    @Test
    @DisplayName("PostgreSQL 的 catalog 为 null、schemaPattern 就是 schema 名")
    void postgresUsesSchemaPattern() {
        PostgresDialect dialect = new PostgresDialect();
        assertNull(dialect.catalog(source("POSTGRESQL", "user_center"), "public"));
        assertEquals("public", dialect.schemaPattern(source("POSTGRESQL", "user_center"), "public"));
    }

    @Test
    @DisplayName("类型名归一：PostgreSQL 内部名映射，MySQL 统一大写")
    void normalizesTypeNames() {
        PostgresDialect postgres = new PostgresDialect();
        assertEquals("INTEGER", postgres.normalizeTypeName("int4"));
        assertEquals("BIGINT", postgres.normalizeTypeName("int8"));
        assertEquals("BOOLEAN", postgres.normalizeTypeName("bool"));
        assertEquals("TIMESTAMP WITH TIME ZONE", postgres.normalizeTypeName("timestamptz"));
        assertEquals("VARCHAR", postgres.normalizeTypeName("varchar"));
        assertNull(postgres.normalizeTypeName(null));

        MySqlDialect mySql = new MySqlDialect();
        assertEquals("INT", mySql.normalizeTypeName("int"));
        assertEquals("VARCHAR", mySql.normalizeTypeName(" varchar "));
    }

    @Test
    @DisplayName("注册表按类型解析，未知类型返回 null")
    void registryResolvesByType() {
        JdbcDialectRegistry registry = new JdbcDialectRegistry(
                List.of(new PostgresDialect(), new MySqlDialect()));

        assertTrue(registry.supports("POSTGRESQL"));
        assertTrue(registry.supports("mysql"));
        assertTrue(!registry.supports("ORACLE"));
        assertEquals("MYSQL", registry.resolve("MYSQL").type());
        assertNull(registry.resolve("DB2"));
        assertEquals("MYSQL / POSTGRESQL", registry.supportedTypes());
    }
}
