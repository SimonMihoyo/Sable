package me.kirara.sable.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 多租户配置 —— 绑定 {@code sable.tenant.*}。
 *
 * <p>覆盖三块：租户 schema 的迁移路径、连接池规模、以及"缺租户即报错"兜底的开关与范围。</p>
 */
@ConfigurationProperties(prefix = "sable.tenant")
public class TenantSchemaProperties {

    /**
     * 租户级迁移脚本的扫描路径（Flyway locations）。
     *
     * <p>这份脚本**每个租户各执行一遍**，建的是 {@code tenant_{id}} 下的 12 张业务表；
     * 应用启动时那条 Flyway 链路只扫 {@code db/migration}，两者不可混。</p>
     */
    private List<String> migrationLocations = new ArrayList<>(List.of("classpath:db/tenant"));

    /**
     * 启动时是否为库里已有租户补齐 schema 与迁移。
     *
     * <p>默认关：生产环境的表结构变更应该由发布流程显式触发，而不是每次重启都跑一遍 DDL。
     * 开发环境（dev profile）默认打开，这样"库里的种子租户"重启后一定能拿到自己的 schema，
     * 不必手工建。</p>
     */
    private boolean provisionOnStartup = false;

    /**
     * 每个租户连接池的最大连接数。
     *
     * <p>连接池是**按租户分开**的（见 {@link TenantDataSourceRegistry} 里关于为什么不用
     * 会话级 search_path 的说明），所以这个数字要按"租户数 × 本值 ≤ 数据库 max_connections"
     * 来估，不能照抄单库实例的 20。</p>
     */
    private int poolMaxSize = 5;

    /** 每个租户连接池的最小空闲连接数。租户多时建议为 0，避免空闲连接占满数据库。 */
    private int poolMinIdle = 0;

    /**
     * "缺租户却要访问租户数据"时是否直接报错（对应 {@link TenantRequiredInterceptor}）。
     *
     * <p>默认开，**不要关**。关掉等于把跨租户读写的风险重新交还给纪律；
     * 只有在排查"某接口莫名 40104 报错"时临时设为 false 用来定位是哪些语句触发的。</p>
     */
    private boolean failFastWithoutTenant = true;

    /**
     * 哪些包下的 Mapper 属于"必须带租户"的范围。
     *
     * <p>判定方式是按 {@code MappedStatement} 的命名空间前缀匹配，而不是去正则解析 SQL
     * —— 后者会被列名、注释里的同名字符串误伤，一个误判就是线上误报。
     * 包名是静态事实，可审查、可枚举。</p>
     *
     * <p>{@code me.kirara.sable.system} 刻意**不在**名单里：那 9 张表是公共表，
     * 登录、租户管理、用户管理都不带租户上下文也应当可用。</p>
     */
    private List<String> tenantScopedPackages = new ArrayList<>(List.of(
            "me.kirara.sable.discovery",
            "me.kirara.sable.classify",
            "me.kirara.sable.audit",
            "me.kirara.sable.assessment",
            "me.kirara.sable.alert",
            "me.kirara.sable.report"));

    public List<String> getMigrationLocations() {
        return migrationLocations;
    }

    public void setMigrationLocations(List<String> migrationLocations) {
        this.migrationLocations = migrationLocations == null ? new ArrayList<>() : migrationLocations;
    }

    public boolean isProvisionOnStartup() {
        return provisionOnStartup;
    }

    public void setProvisionOnStartup(boolean provisionOnStartup) {
        this.provisionOnStartup = provisionOnStartup;
    }

    public int getPoolMaxSize() {
        return poolMaxSize;
    }

    public void setPoolMaxSize(int poolMaxSize) {
        this.poolMaxSize = poolMaxSize;
    }

    public int getPoolMinIdle() {
        return poolMinIdle;
    }

    public void setPoolMinIdle(int poolMinIdle) {
        this.poolMinIdle = poolMinIdle;
    }

    public boolean isFailFastWithoutTenant() {
        return failFastWithoutTenant;
    }

    public void setFailFastWithoutTenant(boolean failFastWithoutTenant) {
        this.failFastWithoutTenant = failFastWithoutTenant;
    }

    public List<String> getTenantScopedPackages() {
        return tenantScopedPackages;
    }

    public void setTenantScopedPackages(List<String> tenantScopedPackages) {
        this.tenantScopedPackages = tenantScopedPackages == null
                ? new ArrayList<>()
                : tenantScopedPackages;
    }
}
