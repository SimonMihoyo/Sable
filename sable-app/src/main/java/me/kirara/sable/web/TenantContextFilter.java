package me.kirara.sable.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.TenantContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文过滤器 — 从请求头 {@code X-Tenant-Id} 解析当前租户并写入 {@link TenantContext}。
 *
 * <p>后续应改为从 JWT 令牌中解析，避免客户端伪造租户 ID。</p>
 */
@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantId = request.getHeader(TENANT_HEADER);
        try {
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContext.setTenantId(Long.valueOf(tenantId));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
