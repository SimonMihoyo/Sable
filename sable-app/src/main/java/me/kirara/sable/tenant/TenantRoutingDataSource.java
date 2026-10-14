package me.kirara.sable.tenant;

import me.kirara.sable.common.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;

/**
 * 按当前租户路由连接 —— MyBatis 与 Flyway 拿到的就是它。
 *
 * <p>路由键就是 {@link TenantContext#getTenantId()}：</p>
 * <ul>
 *   <li>有租户 → 该租户的连接池（search_path = {@code tenant_{id}, public}）
 *       —— 业务表落在租户 schema，9 张公共表仍然可达；</li>
 *   <li>没有租户 → {@code public}。登录、租户管理、用户管理这些系统级接口走的都是这条，
 *       它们只碰公共表，本来就与租户无关。</li>
 * </ul>
 *
 * <p>注意这条"没有租户就落 public"本身**不构成漏洞**：public 里压根没有那 12 张业务表，
 * 漏带租户的查询会直接报 relation 不存在；再往上一层还有
 * {@link TenantRequiredInterceptor} 提前拦下并给出明确的业务错误码。</p>
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final DataSource publicDataSource;
    private final TenantDataSourceRegistry registry;

    public TenantRoutingDataSource(DataSource publicDataSource, TenantDataSourceRegistry registry) {
        this.publicDataSource = publicDataSource;
        this.registry = registry;
    }

    /**
     * 刻意留空。
     *
     * <p>父类的实现要求预先 {@code setTargetDataSources(静态映射)}，而本项目的租户池是
     * 运行期按需产生的（新租户注册后立刻可用，不必重启），所以走
     * {@link #determineTargetDataSource()} 这条扩展点，不经过父类的静态注册表。</p>
     */
    @Override
    public void afterPropertiesSet() {
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        Long tenantId = (Long) determineCurrentLookupKey();
        return tenantId == null ? publicDataSource : registry.getOrCreate(tenantId);
    }
}
