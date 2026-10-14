package me.kirara.sable.tenant;

import com.zaxxer.hikari.HikariDataSource;
import me.kirara.sable.system.service.TenantSchemaProvisioner;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 租户 schema 供给 —— 建 schema、按租户跑迁移、失败时回滚。
 *
 * <p>每个租户 schema 里有一份自己的 {@code flyway_schema_history}，与 public 那份互不干扰：
 * 公共表和应用级迁移走 {@code db/migration}（应用启动时执行一次），
 * 业务表走 {@code db/tenant}（每个租户各执行一次）。</p>
 *
 * <p>失败回滚的判据是"这个 schema 是不是**本次**新建的"：本次新建的才删（连同刚建出来的表），
 * 早已存在的说明是历史环境或别的租户流程留下的，删掉会毁数据，只报错不动手。</p>
 *
 * <h2>为什么迁移要另开一个临时连接池</h2>
 * <p>Flyway 会把连接的当前 schema 切到 {@code defaultSchema}（PostgreSQL 上就是执行
 * {@code SET search_path}）。如果直接借用运行期的租户连接池，迁移结束后那条连接会带着
 * 被改过的 {@code search_path} 回到池里，后续请求复用到它时就可能解析不到
 * {@code public} 下的 9 张公共表。所以这里另建一个**用完即关**的小池，
 * 迁移的连接状态随池一起销毁，绝不回流到运行期连接。</p>
 */
@Component
public class TenantSchemaProvisionerImpl implements TenantSchemaProvisioner {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaProvisionerImpl.class);

    /** 迁移用的临时连接池大小。Flyway 是串行跑的，两条足够（一条迁移 + 一条记历史）。 */
    private static final int SCRATCH_POOL_SIZE = 2;

    private final TenantDataSourceRegistry registry;
    private final DataSourceProperties dataSourceProperties;
    private final TenantSchemaProperties properties;

    public TenantSchemaProvisionerImpl(TenantDataSourceRegistry registry,
                                       DataSourceProperties dataSourceProperties,
                                       TenantSchemaProperties properties) {
        this.registry = registry;
        this.dataSourceProperties = dataSourceProperties;
        this.properties = properties;
    }

    @Override
    public synchronized void provision(Long tenantId) {
        String schema = TenantSchemas.schemaName(tenantId);
        boolean createdNow = !schemaExists(schema);

        try (HikariDataSource scratch = openScratchDataSource(tenantId)) {
            // createSchemas 默认为 true —— schema 不存在时由 Flyway 建，不必自己拼 CREATE SCHEMA
            Flyway flyway = Flyway.configure()
                    .dataSource(scratch)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations(properties.getMigrationLocations().toArray(new String[0]))
                    // 租户 schema 里不该出现"把现状记为基线"这种事：脚本必须从头跑一遍，
                    // 否则一个空 schema 会被当成"结构已存在"，12 张表一张都不会建。
                    .baselineOnMigrate(false)
                    // 与公共库保持一致：不给一键清空租户数据留入口
                    .cleanDisabled(true)
                    .load();

            flyway.migrate();

            if (createdNow) {
                log.info("租户 {} 的 schema {} 已创建并完成初始化", tenantId, schema);
            } else {
                log.debug("租户 {} 的 schema {} 已存在，迁移至最新", tenantId, schema);
            }
        } catch (RuntimeException e) {
            if (createdNow) {
                dropSchema(schema);
                // 万一该租户的连接池已经建出来了，它此刻指向一个已被删掉的 schema，留着没有意义
                registry.evict(tenantId);
            }
            throw new IllegalStateException("租户 " + tenantId + " 的 schema 供给失败（schema=" + schema + "）", e);
        }
    }

    /**
     * 建一个用完即关的迁移专用连接池。
     *
     * <p>{@code currentSchema} 与运行期租户连接保持一致：即便 Flyway 没有切换连接 schema，
     * 脚本里的未限定名也会落到本租户 schema 上，两种情况下行为一致。</p>
     */
    private HikariDataSource openScratchDataSource(Long tenantId) {
        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("sable-tenant-migration-" + tenantId);
        dataSource.addDataSourceProperty("currentSchema", TenantSchemas.searchPath(tenantId));
        dataSource.setMaximumPoolSize(SCRATCH_POOL_SIZE);
        dataSource.setMinimumIdle(0);
        return dataSource;
    }

    private boolean schemaExists(String schema) {
        String sql = "SELECT 1 FROM pg_namespace WHERE nspname = ?";
        try (Connection connection = registry.publicDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("检查 schema " + schema + " 是否存在时出错", e);
        }
    }

    /**
     * 回滚：删掉本次刚建出来的 schema。
     *
     * <p>schema 名由 {@link TenantSchemas#schemaName} 从**数字 ID** 拼出，不含任何外部输入，
     * 因此直接拼接不构成注入面。DDL 也无法参数化 —— PostgreSQL 的标识符不能用占位符传。</p>
     *
     * <p>这里不向外抛异常：调用方正在处理最初的失败，再抛一个"回滚也失败了"只会盖掉根因。
     * 删不掉时留下明确的 ERROR 日志交人工处理。</p>
     */
    private void dropSchema(String schema) {
        try (Connection connection = registry.publicDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            log.warn("已回滚租户 schema {}（本次新建，建表失败）", schema);
        } catch (SQLException e) {
            log.error("回滚租户 schema {} 失败，需要人工清理", schema, e);
        }
    }
}
