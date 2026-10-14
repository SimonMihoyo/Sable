package me.kirara.sable.tenant;

import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.TenantContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@DisplayName("缺租户兜底拦截器")
class TenantRequiredInterceptorTest {

    /** 假执行结果。类型必须是 List —— Executor.query 的返回类型就是它，Mockito 会核对。 */
    private static final List<Object> PROCEEDED = List.<Object>of("已执行");

    private final TenantSchemaProperties properties = new TenantSchemaProperties();
    private final TenantRequiredInterceptor interceptor = new TenantRequiredInterceptor(properties);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 造一个可被 MyBatis 接受的调用现场。
     *
     * <p>目标必须是**真正的插件目标**（{@code Executor}）—— 随便写个类的同签名方法，
     * MyBatis 在构造 {@code Invocation} 时会因"方法不属于任何插件接口"直接拒绝。</p>
     */
    private Invocation invocationFor(String statementId) throws Exception {
        Executor executor = mock(Executor.class);
        // 用 doReturn 而不是 when(...).thenReturn(...)：Executor.query 的返回类型是 List<E>，
        // 直接 thenReturn 会被泛型推断卡在编译期。
        // 四个参数都用无类型的 any()：带类型的 any(Xxx.class) 在 Mockito 2+ 里**不匹配 null**，
        // 而这里的参数值本来就是 null（真实的 SQL 参数与结果处理器）。
        doReturn(PROCEEDED).when(executor).query(any(), any(), any(), any());

        // 只关心命名空间，SQL 内容无关紧要 —— SqlSource 是函数式接口，给个空实现即可
        SqlSource sqlSource = parameterObject -> null;
        MappedStatement statement = new MappedStatement.Builder(
                new Configuration(), statementId, sqlSource, SqlCommandType.SELECT).build();

        Method method = Executor.class.getMethod("query",
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        return new Invocation(executor, method, new Object[]{statement, null, RowBounds.DEFAULT, null});
    }

    @Test
    @DisplayName("业务模块的语句没有租户上下文时抛 40104，不放行")
    void blocksTenantScopedStatementWithoutTenant() throws Throwable {
        Invocation invocation = invocationFor("me.kirara.sable.discovery.mapper.DataSourceMapper.selectById");

        BusinessException e = assertThrows(BusinessException.class, () -> interceptor.intercept(invocation));
        assertEquals(ErrorCode.TENANT_CONTEXT_MISSING.getCode(), e.getCode());
        // 关键在"抛错"而不是"继续执行"：放行就等于让这条查询落到 public 去找同名表
    }

    @Test
    @DisplayName("有租户上下文时正常放行")
    void allowsWhenTenantPresent() throws Throwable {
        TenantContext.setTenantId(1001L);

        Object result = interceptor.intercept(
                invocationFor("me.kirara.sable.discovery.mapper.DataSourceMapper.selectById"));

        assertEquals(PROCEEDED, result);
    }

    @Test
    @DisplayName("六个业务模块全部在拦截范围内")
    void coversEveryBusinessModule() throws Throwable {
        List<String> modules = List.of("discovery", "classify", "audit", "assessment", "alert", "report");
        for (String module : modules) {
            Invocation invocation = invocationFor("me.kirara.sable." + module + ".mapper.SomeMapper.selectList");
            assertThrows(BusinessException.class, () -> interceptor.intercept(invocation), module);
        }
    }

    @Test
    @DisplayName("system 模块是公共表，没有租户也必须放行（否则登录就挂了）")
    void allowsSystemModuleWithoutTenant() throws Throwable {
        Object result = interceptor.intercept(invocationFor("me.kirara.sable.system.mapper.SysUserMapper.selectOne"));

        assertEquals(PROCEEDED, result);
    }

    @Test
    @DisplayName("关掉开关后只放行不报错 —— 仅供排查误报时临时使用")
    void canBeTurnedOff() throws Throwable {
        TenantSchemaProperties relaxed = new TenantSchemaProperties();
        relaxed.setFailFastWithoutTenant(false);
        TenantRequiredInterceptor off = new TenantRequiredInterceptor(relaxed);

        Object result = off.intercept(invocationFor("me.kirara.sable.discovery.mapper.DataSourceMapper.selectById"));

        assertEquals(PROCEEDED, result);
    }
}
