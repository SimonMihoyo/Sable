package me.kirara.sable.discovery.connect;

import me.kirara.sable.discovery.connect.dialect.JdbcDialectRegistry;
import me.kirara.sable.discovery.connect.dialect.MySqlDialect;
import me.kirara.sable.discovery.connect.dialect.PostgresDialect;
import me.kirara.sable.discovery.dto.ConnectionTestResult;
import me.kirara.sable.discovery.engine.MetadataScanEngineImpl;
import me.kirara.sable.discovery.engine.MetadataSnapshot;
import me.kirara.sable.discovery.entity.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL 方言的**真库**集成测试（Testcontainers）。
 *
 * <p>为什么必须真库：MySQL 与 PostgreSQL 的差异恰好都在元数据层 —— 没有 schema（库即
 * catalog）、注释要 {@code useInformationSchema=true} 才读得到、类型名是大写。
 * 单测只能证明"参数拼对了"，证明不了"扫出来的东西对"。这也是 AGENTS §5.6 里
 * "CI 覆盖不到、要真库"的那一类，只是这里用容器把真库搬进了测试。</p>
 *
 * <p>本地没有 Docker 时整类跳过（{@code disabledWithoutDocker}）；GitHub Actions 的
 * runner 自带 Docker，CI 上会执行。没有 Docker 时至少还有单测覆盖 URL / schema 语义 /
 * 类型归一，以及一次手工的真 MySQL 接口验证。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlMetadataScanIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("crm_demo")
            .withUsername("sable")
            .withPassword("sable")
            .withInitScript("db/test/mysql_init.sql");

    private final JdbcDialectRegistry registry = new JdbcDialectRegistry(
            List.of(new PostgresDialect(), new MySqlDialect()));

    private JdbcConnectionFactory factory() {
        return new JdbcConnectionFactory(registry);
    }

    private DataSource dataSource() {
        DataSource source = new DataSource();
        source.setType("MYSQL");
        source.setHost(MYSQL.getHost());
        source.setPort(MYSQL.getMappedPort(3306));
        source.setDatabaseName("crm_demo");
        source.setUsername("sable");
        // 容器里没有 TLS，且 caching_sha2_password 首次认证需要取公钥
        source.setJdbcOptions("useSSL=false&allowPublicKeyRetrieval=true");
        return source;
    }

    @Test
    @DisplayName("只扫配置的库：注释、主键、类型名都正确，且看不到 other_db")
    void scansConfiguredDatabaseOnly() throws Exception {
        DataSource source = dataSource();
        JdbcConnectionFactory factory = factory();
        assertTrue(factory.isTestable(source), "MySQL 驱动应可用");

        MetadataSnapshot snapshot;
        try (Connection connection = factory.open(source, "sable")) {
            snapshot = new MetadataScanEngineImpl(registry).scan(source, connection, () -> false);
        }

        // other_db 里有表，但一个数据源只扫它配置的那个库
        assertEquals(1, snapshot.schemaCount(), "只应扫到 crm_demo");
        MetadataSnapshot.Schema schema = snapshot.schemas().get(0);
        assertEquals("crm_demo", schema.name());

        assertEquals(1, schema.tables().size());
        MetadataSnapshot.Table table = schema.tables().get(0);
        assertEquals("customer", table.name());
        assertEquals("TABLE", table.type());
        assertEquals("客户主表", table.comment(), "表注释要能读到（useInformationSchema）");

        assertEquals(4, table.columns().size());
        MetadataSnapshot.Column id = column(table, "id");
        assertTrue(id.primaryKey());
        assertEquals("BIGINT", id.dataType());

        MetadataSnapshot.Column phone = column(table, "phone");
        assertEquals("VARCHAR", phone.dataType());
        assertEquals("手机号", phone.comment(), "字段注释要能读到");
        assertFalse(phone.nullable());
        assertEquals(1, phone.ordinal());

        MetadataSnapshot.Column fullName = column(table, "full_name");
        assertEquals("姓名", fullName.comment());
        assertTrue(fullName.nullable());
    }

    @Test
    @DisplayName("连通性探测走同一套方言，能返回 MySQL 版本")
    void connectionTesterWorksAgainstMySql() {
        ConnectionTestResult result = new JdbcConnectionTester(factory()).test(dataSource(), "sable");

        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(result.getServerVersion().contains("MySQL"), result.getServerVersion());
    }

    private static MetadataSnapshot.Column column(MetadataSnapshot.Table table, String name) {
        return table.columns().stream()
                .filter(column -> column.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少字段 " + name));
    }
}
