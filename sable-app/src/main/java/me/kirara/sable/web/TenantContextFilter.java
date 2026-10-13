package me.kirara.sable.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户上下文兜底过滤器 — 仅当令牌未给出租户时，才从请求头 {@code X-Tenant-Id} 读取。
 *
 * <p><b>租户的可信来源是令牌里的 {@code tid} 声明</b>，由 {@link JwtAuthenticationFilter}
 * 先行写入 {@link TenantContext}。请求头可以被客户端随意伪造，因此只在两种情况下才生效：
 * 令牌没有租户归属的系统级用户，以及白名单路径。它们的作用是保留 API 设计文档 1.4 节
 * 所述"超级管理员跨租户操作"的能力。</p>
 *
 * <p>顺序必须晚于 {@link JwtAuthenticationFilter}，否则会先被请求头抢填。</p>
 */
@Component
@Order(2)
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean filledFromHeader = false;
        try {
            if (TenantContext.getTenantId() == null) {
                filledFromHeader = applyHeader(request);
            }
            filterChain.doFilter(request, response);
        } finally {
            if (filledFromHeader) {
                // 只清理自己填进去的值；令牌填的由 JwtAuthenticationFilter 负责清理
                TenantContext.clear();
            }
        }
    }

    /**
     * 用请求头填充租户上下文。
     *
     * <p>请求头非法时不报错也不填充 —— 它是可选覆盖项，写错就等于没写。
     * 拒绝请求会把一个配置笔误升级成故障，而"没有租户"在后续按租户取数的逻辑里
     * 自有更明确的判断。</p>
     *
     * @return 是否实际填入了租户
     */
    private boolean applyHeader(HttpServletRequest request) {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        try {
            TenantContext.setTenantId(Long.valueOf(tenantId.trim()));
            return true;
        } catch (NumberFormatException e) {
            log.warn("忽略非法的 {} 请求头: {}", TENANT_HEADER, tenantId);
            return false;
        }
    }
}
