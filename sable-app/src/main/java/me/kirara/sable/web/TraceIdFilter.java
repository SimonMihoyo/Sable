package me.kirara.sable.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.TraceContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 链路标识过滤器 — 为每个请求分配 traceId 并写入日志上下文（MDC），
 * 同时回写 {@code X-Request-Id} 响应头。
 *
 * <p>它是过滤链里最外层的过滤器（{@code @Order(0)}，早于 {@code @Order(1)} 的
 * {@link JwtAuthenticationFilter}），原因有两个：认证失败的告警日志也要带上 traceId；
 * 且 MDC 的清理必须晚于所有写日志的环节，放在 finally 里最稳妥。</p>
 *
 * <p><b>关于不信任调用方传来的标识：</b>请求头里的值会被写进日志，因此只接受
 * 「长度 1-64 的安全字符（字母、数字、点、下划线、连字符）」这一形态 —— 防止把换行、
 * 引号之类的东西注入日志（伪造日志行）。不合法时静默改用新生成的标识，
 * 不报错：这是可选的对账字段，值不对就等于没传。</p>
 */
@Component
@Order(0)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 允许透传的调用方标识：够长够用，且不含任何可破坏日志行的字符。 */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        TraceContext.setTraceId(traceId);
        try {
            if (!response.isCommitted()) {
                response.setHeader(TraceContext.REQUEST_ID_HEADER, traceId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }

    /** 取调用方标识，缺失或形态不合法时重新生成。 */
    private String resolveTraceId(HttpServletRequest request) {
        String fromClient = request.getHeader(TraceContext.REQUEST_ID_HEADER);
        if (fromClient != null && SAFE_REQUEST_ID.matcher(fromClient).matches()) {
            return fromClient;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
