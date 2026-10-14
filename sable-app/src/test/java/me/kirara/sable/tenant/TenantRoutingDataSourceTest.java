package me.kirara.sable.tenant;

import com.zaxxer.hikari.HikariDataSource;
import me.kirara.sable.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("按租户路由数据源")
class TenantRoutingDataSourceTest {

    /** 假的 public 数据源 —— 只用来比较"路由到了哪一个"，不真的连库。 */
    private final DataSource publicDataSource = new HikariDataSource();

    private final TenantSchemaProperties tenantProperties = new TenantSchemaProperties();

    private final TenantDataSourceRegistry registry = new TenantDataSourceRegistry(
            publicDataSource, dataSourceProperties(), tenantProperties);

    private final TenantRoutingDataSource routing = new TenantRoutingDataSource(publicDataSource, registry);

    private static DataSourceProperties dataSourceProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/never-connected");
        properties.setUsername("nobody");
        properties.setPassword("nobody");
        return properties;
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        registry.destroy();
    }

    @Test
    @DisplayName("没有租户上下文时落 public —— 登录、租户管理走的就是这条")
    void fallsBackToPublicWithoutTenant() {
        assertSame(publicDataSource, routing.determineTargetDataSource());
        assertEquals(0, registry.poolCount(), "没有租户就不该建连接池");
    }

    @Test
    @DisplayName("有租户上下文时路由到该租户的连接池，池名与租户对应")
    void routesToTenantPool() {
        TenantContext.setTenantId(1001L);

        DataSource target = routing.determineTargetDataSource();

        assertNotSame(publicDataSource, target);
        assertTrue(target instanceof HikariDataSource);
        assertEquals("sable-tenant-1001", ((HikariDataSource) target).getPoolName());
    }

    @Test
    @DisplayName("同一租户复用同一个池，不会每次查询都新建")
    void reusesPoolPerTenant() {
        TenantContext.setTenantId(1001L);

        assertSame(routing.determineTargetDataSource(), routing.determineTargetDataSource());
        assertEquals(1, registry.poolCount());
    }

    @Test
    @DisplayName("不同租户落在不同的池上")
    void separatesTenants() {
        TenantContext.setTenantId(1L);
        DataSource first = routing.determineTargetDataSource();
        TenantContext.setTenantId(2L);
        DataSource second = routing.determineTargetDataSource();

        assertNotSame(first, second);
        assertEquals(2, registry.poolCount());
    }

    @Test
    @DisplayName("租户内的连接把 schema 绑在连接参数上，而不是靠会话态 search_path")
    void tenantPoolBindsSchemaAsConnectionProperty() {
        TenantContext.setTenantId(7L);

        HikariDataSource pool = (HikariDataSource) routing.determineTargetDataSource();

        // currentSchema 随建连参数下发，连接一建立归属就定了 —— 不需要"用完复位"，
        // 也就不存在异常路径漏掉复位而串租户的可能
        assertEquals("tenant_7,public", pool.getDataSourceProperties().getProperty("currentSchema"));
    }

    @Test
    @DisplayName("丢弃租户池后下次访问会重建（供给失败回滚时会用到）")
    void evictDropsPool() {
        TenantContext.setTenantId(5L);
        routing.determineTargetDataSource();
        assertEquals(1, registry.poolCount());

        registry.evict(5L);

        assertEquals(0, registry.poolCount());
        routing.determineTargetDataSource();
        assertEquals(1, registry.poolCount());
    }
}
