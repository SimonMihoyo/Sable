package me.kirara.sable.web;

import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantContextFilter} 单元测试 — 验证"令牌优先、请求头兜底"这条规则。
 *
 * <p>前置条件通过直接给 {@link TenantContext} 赋值来模拟 —— 真实运行中这一步由
 * {@link JwtAuthenticationFilter} 完成。</p>
 */
class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TraceContext.clear();
    }

    @Test
    @DisplayName("令牌已给出租户时忽略请求头")
    void tokenWinsOverHeader() throws Exception {
        TenantContext.setTenantId(1001L);

        Long[] seen = {-1L};
        filter.doFilter(request("9999"), new MockHttpServletResponse(),
                (req, res) -> seen[0] = TenantContext.getTenantId());

        assertEquals(1001L, seen[0].longValue(), "请求头不得覆盖令牌中的租户");
    }

    @Test
    @DisplayName("令牌未给出租户时用请求头兜底（超管跨租户场景）")
    void headerFillsWhenTokenHasNoTenant() throws Exception {
        Long[] seen = {null};
        String[] seenMdcTenantId = {null};
        filter.doFilter(request("1001"), new MockHttpServletResponse(),
                (req, res) -> {
                    seen[0] = TenantContext.getTenantId();
                    seenMdcTenantId[0] = MDC.get(TraceContext.TENANT_ID);
                });

        assertEquals(1001L, seen[0].longValue());
        assertEquals("1001", seenMdcTenantId[0], "兜底得来的租户同样要进日志上下文");
        // 自己填的租户要自己清理
        assertNull(TenantContext.getTenantId());
        assertNull(MDC.get(TraceContext.TENANT_ID), "日志上下文里的租户也要一并撤销");
    }

    @Test
    @DisplayName("没有请求头时不填充，也不报错")
    void noHeaderNoTenant() throws Exception {
        Long[] seen = {-1L};
        filter.doFilter(request(null), new MockHttpServletResponse(),
                (req, res) -> seen[0] = TenantContext.getTenantId());

        assertNull(seen[0]);
    }

    @Test
    @DisplayName("请求头非法时忽略而不抛异常")
    void malformedHeaderIsIgnored() throws Exception {
        Long[] seen = {-1L};
        filter.doFilter(request("not-a-number"), new MockHttpServletResponse(),
                (req, res) -> seen[0] = TenantContext.getTenantId());

        assertNull(seen[0]);
        assertTrue(true, "非法请求头不应中断请求");
    }

    private MockHttpServletRequest request(String tenantHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/roles");
        if (tenantHeader != null) {
            request.addHeader(TenantContextFilter.TENANT_HEADER, tenantHeader);
        }
        return request;
    }
}
