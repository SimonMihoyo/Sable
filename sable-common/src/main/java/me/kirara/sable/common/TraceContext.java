package me.kirara.sable.common;

import org.slf4j.MDC;

/**
 * 日志上下文 — 把请求级身份写进 SLF4J 的 MDC，使每行日志都自带
 * traceId / tenantId / userId（输出格式见 sable-app/src/main/resources/logback-spring.xml）。
 *
 * <p>与 {@link TenantContext}、{@link UserContext} 的分工：那两个是<b>业务</b>上下文，
 * 供代码读取身份做数据过滤与鉴权判断；本类只服务<b>日志</b>，代码不该依赖它做业务决策。
 * 两者由同一批过滤器写入，但生命周期相互独立 —— 日志上下文由最外层的
 * TraceIdFilter 统一清理。</p>
 *
 * <p>键名是日志格式的一部分，改动会同时影响所有已采集的日志，不要随意重命名。</p>
 */
public class TraceContext {

    /** 一次请求一个标识；调用方可用同名请求头自带（见 {@link #REQUEST_ID_HEADER}）。 */
    public static final String TRACE_ID = "traceId";

    /** 租户标识，取自令牌 tid 声明。 */
    public static final String TENANT_ID = "tenantId";

    /** 用户标识，取自令牌 userId 声明。 */
    public static final String USER_ID = "userId";

    /** 调用方自带请求标识所用的请求头：传了就用它，同时回写到响应头，便于跨系统对账。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static void setTraceId(String traceId) {
        put(TRACE_ID, traceId);
    }

    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    public static void setTenantId(Long tenantId) {
        put(TENANT_ID, tenantId);
    }

    public static void setUserId(Long userId) {
        put(USER_ID, userId);
    }

    /**
     * 写 MDC。值为 null 表示"没有" —— 此时移除该键而不是留下上一行日志的残留值，
     * 否则同一线程复用时会打印出早已不成立的租户或用户。
     */
    private static void put(String key, Object value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, String.valueOf(value));
        }
    }

    /** 清理日志上下文。由最外层过滤器在 finally 中调用，避免线程复用导致的串号。 */
    public static void clear() {
        MDC.remove(TRACE_ID);
        MDC.remove(TENANT_ID);
        MDC.remove(USER_ID);
    }
}
