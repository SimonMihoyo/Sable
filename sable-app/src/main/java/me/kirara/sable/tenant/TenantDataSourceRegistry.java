package me.kirara.sable.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 租户连接池注册表 —— 一个租户一个池，池里每条连接的 search_path 在建池时就固定。
 *
 * <h2>为什么按租户开池，而不是共用一个池再切换 search_path</h2>
 * <p>{@code search_path} 是**连接级**的会话状态。共用池意味着"借出时设置、归还时复位"，
 * 而复位这一步一旦在异常路径上漏掉，下一个用到这条连接的请求就落在别人的 schema 里
 * —— 串租户，且不留痕迹。设计文档 §5.2 的样例正是这个形态（在拦截器里对一条
 * {@code jdbcTemplate} 连接执行 {@code SET search_path}），那条连接多半还不是 MyBatis
 * 随后执行 SQL 用的那一条，连"设置成功"都保证不了。</p>
 *
 * <p>本类换了个思路：把 schema 写进**连接本身的建连参数**（PostgreSQL JDBC 的
 * {@code currentSchema}），于是每条连接的归属在建立那一刻就确定了，不存在"忘记复位"。
 * 代价是连接池按租户分组、数据库总连接数约为"租户数 × {@code sable.tenant.pool-max-size}"，
 * 所以要按租户数估算 {@code max_connections}，见 {@link TenantSchemaProperties}。</p>
 */
public class TenantDataSourceRegistry implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TenantDataSourceRegistry.class);

    private final DataSource publicDataSource;
    private final DataSourceProperties dataSourceProperties;
    private final TenantSchemaProperties tenantProperties;
    private final Map<Long, HikariDataSource> tenantPools = new ConcurrentHashMap<>();

    public TenantDataSourceRegistry(DataSource publicDataSource,
                                    DataSourceProperties dataSourceProperties,
                                    TenantSchemaProperties tenantProperties) {
        this.publicDataSource = publicDataSource;
        this.dataSourceProperties = dataSourceProperties;
        this.tenantProperties = tenantProperties;
    }

    /** public schema 的连接 —— 建 schema、查租户表是否存在这类"管理动作"用它。 */
    public DataSource publicDataSource() {
        return publicDataSource;
    }

    /**
     * 取某租户的连接池，没有就建一个。
     *
     * <p>池在第一次用到时才建立（懒加载）：租户很多但只有少数活跃时，不必为空租户占连接。
     * 目标 schema 此时可能还不存在 —— 这没问题，{@code search_path} 里的名字是查询时才解析的，
     * schema 建好后同一条连接立刻就能解析到。</p>
     */
    public DataSource getOrCreate(Long tenantId) {
        return tenantPools.computeIfAbsent(tenantId, this::createPool);
    }

    private HikariDataSource createPool(Long tenantId) {
        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("sable-tenant-" + tenantId);
        // 隔离的落点：currentSchema 是 PostgreSQL JDBC 的连接参数，连上即生效
        dataSource.addDataSourceProperty("currentSchema", TenantSchemas.searchPath(tenantId));
        dataSource.setMaximumPoolSize(tenantProperties.getPoolMaxSize());
        dataSource.setMinimumIdle(tenantProperties.getPoolMinIdle());
        log.info("为租户 {} 建立连接池 {}", tenantId, dataSource.getPoolName());
        return dataSource;
    }

    /** 丢弃某租户的连接池（供给失败回滚时用，避免留着指向已删 schema 的空池）。 */
    public void evict(Long tenantId) {
        HikariDataSource pool = tenantPools.remove(tenantId);
        if (pool != null) {
            pool.close();
            log.info("已关闭租户 {} 的连接池", tenantId);
        }
    }

    /** 当前已建立的租户池数量 —— 供健康检查与测试观察。 */
    public int poolCount() {
        return tenantPools.size();
    }

    // TODO 租户被删除时关掉它的连接池 —— 租户删除（含级联清理 schema）尚未实现，
    //      在那之前池会一直留着，属于已知的小泄漏（租户数量级下可忽略）。

    @Override
    public void destroy() {
        tenantPools.values().forEach(HikariDataSource::close);
        tenantPools.clear();
    }
}
