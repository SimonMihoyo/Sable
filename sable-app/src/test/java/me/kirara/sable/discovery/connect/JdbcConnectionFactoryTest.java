package me.kirara.sable.discovery.connect;

import me.kirara.sable.discovery.connect.dialect.JdbcDialectRegistry;
import me.kirara.sable.discovery.connect.dialect.MySqlDialect;
import me.kirara.sable.discovery.connect.dialect.PostgresDialect;
import me.kirara.sable.discovery.entity.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JdbcConnectionFactory} 的 URL 拼装与可连接性判断。
 *
 * <p>只测纯逻辑：真正连库（含失败路径、状态回写）按 AGENTS §5.3 打真实接口验证，
 * MySQL 的元数据行为另见 Testcontainers 集成测试。</p>
 */
class JdbcConnectionFactoryTest {

    private final JdbcConnectionFactory factory = new JdbcConnectionFactory(
            new JdbcDialectRegistry(List.of(new PostgresDialect(), new MySqlDialect())));

    private static DataSource source(String type, String options) {
        DataSource source = new DataSource();
        source.setType(type);
        source.setHost("db.internal");
        source.setPort(5432);
        source.setDatabaseName("user_center");
        source.setUsername("readonly");
        source.setJdbcOptions(options);
        return source;
    }

    @Test
    @DisplayName("PostgreSQL URL：带 host/port/database，附加参数以 ? 拼接")
    void buildsPostgresUrl() {
        assertEquals("jdbc:postgresql://db.internal:5432/user_center",
                factory.buildUrl(source("POSTGRESQL", null)));
        assertEquals("jdbc:postgresql://db.internal:5432/user_center?sslmode=disable",
                factory.buildUrl(source("POSTGRESQL", "sslmode=disable")));
    }

    @Test
    @DisplayName("MySQL URL：库名在路径上，附加参数以 ? 拼接")
    void buildsMySqlUrl() {
        assertEquals("jdbc:mysql://db.internal:5432/user_center",
                factory.buildUrl(source("MYSQL", null)));
        assertEquals("jdbc:mysql://db.internal:5432/user_center?useSSL=true",
                factory.buildUrl(source("MYSQL", "useSSL=true")));
    }

    @Test
    @DisplayName("用户手写的前导 ? / & 会被剥掉，不产生 ?? 或 ?&")
    void normalizesLeadingSeparators() {
        assertEquals("jdbc:postgresql://db.internal:5432/user_center?sslmode=require",
                factory.buildUrl(source("POSTGRESQL", "?sslmode=require")));
        assertEquals("jdbc:mysql://db.internal:5432/user_center?useSSL=false",
                factory.buildUrl(source("MYSQL", "&useSSL=false")));
    }

    @Test
    @DisplayName("Oracle / SQL Server / NoSQL：方言尚未引入，拼 URL 直接拒绝")
    void rejectsUnsupportedTypes() {
        assertThrows(IllegalArgumentException.class, () -> factory.buildUrl(source("ORACLE", null)));
        assertThrows(IllegalArgumentException.class, () -> factory.buildUrl(source("SQLSERVER", null)));
        assertThrows(IllegalArgumentException.class, () -> factory.buildUrl(source("MONGODB", null)));
        assertThrows(IllegalArgumentException.class, () -> factory.buildUrl(source("REDIS", null)));
    }

    @Test
    @DisplayName("可连接性：PostgreSQL 与 MySQL 有方言+驱动；其余不可")
    void testabilityFollowsDialectsAndDrivers() {
        assertTrue(factory.isTestable(source("POSTGRESQL", null)));
        assertTrue(factory.isTestable(source("MYSQL", null)));
        assertFalse(factory.isTestable(source("ORACLE", null)));
        assertFalse(factory.isTestable(source("SQLSERVER", null)));
        assertFalse(factory.isTestable(source("MONGODB", null)));
        assertFalse(factory.isTestable(source("REDIS", null)));
    }

    @Test
    @DisplayName("不可连接的原因：区分'没有方言'与'缺驱动'")
    void explainsWhyUntestable() {
        String mongodb = factory.untestableReason(source("MONGODB", null));
        assertTrue(mongodb.contains("暂不支持 MONGODB"), mongodb);
        assertTrue(mongodb.contains("当前支持 MYSQL / POSTGRESQL"), mongodb);
    }
}
