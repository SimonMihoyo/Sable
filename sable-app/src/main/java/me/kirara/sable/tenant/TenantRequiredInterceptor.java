package me.kirara.sable.tenant;

import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.TenantContext;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 缺租户兜底 —— 语句属于租户范围、但当前上下文里没有租户时，直接抛错。
 *
 * <p>这是多租户三件套里的第三件（路由、供给、兜底），防的是一类最危险的失败：
 * 某条业务查询漏掉了租户上下文，于是连接路由到 public 去执行。表不同名时它表现为
 * "relation does not exist"，虽然难看但至少响亮；一旦恰好同名（或将来有人往 public
 * 补了同名表），它就变成**静默地读了别的租户的数据** —— 对合规产品这是最不能接受的形态。
 * 所以宁可在这里把请求打断。</p>
 *
 * <h2>为什么按命名空间判断，而不是解析 SQL</h2>
 * <p>解析 SQL 需要正则去找表名，列名、字符串常量、注释里出现同名词都会误判，
 * 一个误判就是线上误报；而命名空间是静态事实，一看就知道某个 Mapper 属于哪个域。</p>
 *
 * <h2>边界</h2>
 * <p>{@code me.kirara.sable.system} 下的 Mapper 不在范围内：那 9 张是公共表，
 * 登录、租户管理、用户管理本来就不该依赖租户上下文。范围的配置见
 * {@link TenantSchemaProperties#getTenantScopedPackages()}。</p>
 *
 * <p>拦截点选 {@code Executor} 而不是 {@code StatementHandler}：前者能拿到
 * {@code MappedStatement}（命名空间与语句 id 都在里面），且位于一级/二级缓存之前，
 * 命中缓存时同样会被拦到 —— 若是拦在 {@code StatementHandler}，走缓存就不经过它了。</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class TenantRequiredInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantRequiredInterceptor.class);

    private final boolean failFast;
    private final List<String> tenantScopedPackages;

    public TenantRequiredInterceptor(TenantSchemaProperties properties) {
        this.failFast = properties.isFailFastWithoutTenant();
        // 复制一份并去掉尾部，避免调用方后续改动配置对象影响这里的匹配
        this.tenantScopedPackages = properties.getTenantScopedPackages().stream()
                .map(String::trim)
                .filter(pkg -> !pkg.isEmpty())
                .toList();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        if (failFast && TenantContext.getTenantId() == null && isTenantScoped(statement)) {
            // 记日志是为了能直接定位到是哪个 Mapper 方法漏了租户上下文 ——
            // 异常信息里带 traceId（见 logback-spring.xml 的 pattern），排查时能串起整条链路
            log.warn("缺少租户上下文的租户范围查询被拦截：statement={}", statement.getId());
            throw new BusinessException(ErrorCode.TENANT_CONTEXT_MISSING);
        }
        return invocation.proceed();
    }

    /** 该语句是否属于"必须带租户"的范围。 */
    private boolean isTenantScoped(MappedStatement statement) {
        String id = statement.getId();
        for (String pkg : tenantScopedPackages) {
            if (id.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }
}
