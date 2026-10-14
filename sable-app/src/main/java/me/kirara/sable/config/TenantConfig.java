package me.kirara.sable.config;

import com.zaxxer.hikari.HikariDataSource;
import me.kirara.sable.tenant.TenantDataSourceRegistry;
import me.kirara.sable.tenant.TenantRequiredInterceptor;
import me.kirara.sable.tenant.TenantRoutingDataSource;
import me.kirara.sable.tenant.TenantSchemaProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 多租户存储侧装配 —— 把容器里的 {@link DataSource} 换成按租户路由的那个。
 *
 * <p>本类只做装配，机制分别在 {@code me.kirara.sable.tenant} 下三个类里。这里解释两处
 * 看起来多余、其实必须的写法：</p>
 *
 * <ol>
 *   <li><b>为什么自己声明 {@code publicDataSource} 这个 Bean</b>：Boot 的连接池自动配置带着
 *       {@code @ConditionalOnMissingBean(DataSource.class)}，一旦我们提供了路由数据源，
 *       它就会整体退让 —— {@code spring.datasource.hikari.*} 那一堆连接池参数随之失效。
 *       所以这里用 Boot 自己的那套写法（{@code DataSourceProperties} + 把
 *       {@code spring.datasource.hikari} 绑定到实例上）把 public 数据源显式声明出来，
 *       保证 {@code maximum-pool-size} 之类的既有配置继续生效。</li>
 *   <li><b>为什么路由数据源标 {@code @Primary}</b>：容器里因此有两个 {@code DataSource}。
 *       标了主候选，MyBatis 与 Flyway 那些 {@code @ConditionalOnSingleCandidate(DataSource)}
 *       的自动配置才会照常工作，并且拿到的是路由这一个。</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(TenantSchemaProperties.class)
public class TenantConfig {

    /**
     * public schema 的数据源，也是"没有租户上下文"时的落点。
     *
     * <p>写法与 Boot 的 {@code DataSourceConfiguration.Hikari} 一致，见类注释第 1 点。</p>
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource publicDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public TenantDataSourceRegistry tenantDataSourceRegistry(HikariDataSource publicDataSource,
                                                             DataSourceProperties dataSourceProperties,
                                                             TenantSchemaProperties tenantSchemaProperties) {
        return new TenantDataSourceRegistry(publicDataSource, dataSourceProperties, tenantSchemaProperties);
    }

    /** MyBatis 与 Flyway 真正使用的数据源。 */
    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource publicDataSource, TenantDataSourceRegistry registry) {
        return new TenantRoutingDataSource(publicDataSource, registry);
    }

    /**
     * 缺租户兜底拦截器。
     *
     * <p>声明成普通 {@code Interceptor} Bean 即可 —— MyBatis-Plus 的自动配置会收集容器里的
     * 所有 {@code Interceptor} 装进 {@code SqlSessionFactory}，与 {@link MyBatisPlusConfig}
     * 里那个分页拦截器并列。</p>
     */
    @Bean
    public TenantRequiredInterceptor tenantRequiredInterceptor(TenantSchemaProperties properties) {
        return new TenantRequiredInterceptor(properties);
    }
}
