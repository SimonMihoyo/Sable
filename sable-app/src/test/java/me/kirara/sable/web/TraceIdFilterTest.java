package me.kirara.sable.web;

import me.kirara.sable.common.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TraceIdFilter} 单元测试 — 覆盖标识的来源、透传与清理。
 *
 * <p>重点在"不信任调用方送来的值"：请求头里的内容会被写进日志，形态不合法就必须
 * 换成自己生成的，否则等于把日志行交给调用方拼。</p>
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("没有请求头时生成新标识，并在链路内与响应头中一致")
    void generatesTraceId() throws Exception {
        String[] seen = {null};
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(null), response, (req, res) -> seen[0] = TraceContext.getTraceId());

        assertNotNull(seen[0], "链路内必须能读到标识");
        assertEquals(32, seen[0].length(), "生成的是去掉连字符的 UUID");
        assertEquals(seen[0], response.getHeader(TraceContext.REQUEST_ID_HEADER), "响应头需回写同一个标识");
    }

    @Test
    @DisplayName("合法请求头被透传，便于跨系统对账")
    void safeRequestIdPassesThrough() throws Exception {
        String[] seen = {null};
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("order-svc.7f3a"), response, (req, res) -> seen[0] = TraceContext.getTraceId());

        assertEquals("order-svc.7f3a", seen[0]);
        assertEquals("order-svc.7f3a", response.getHeader(TraceContext.REQUEST_ID_HEADER));
    }

    @Test
    @DisplayName("含换行的请求头被替换 —— 防止伪造日志行")
    void newlineInRequestIdIsReplaced() throws Exception {
        String[] seen = {null};

        filter.doFilter(request("abc\n2026-01-01 00:00:00 ERROR 伪造的一行日志"), new MockHttpServletResponse(),
                (req, res) -> seen[0] = TraceContext.getTraceId());

        assertNotEquals("abc\n2026-01-01 00:00:00 ERROR 伪造的一行日志", seen[0]);
        assertEquals(32, seen[0].length(), "不合法时改用自己生成的标识");
    }

    @Test
    @DisplayName("超长请求头被替换 —— 形态不合法的值一律不采信")
    void overlongRequestIdIsReplaced() throws Exception {
        String[] seen = {null};

        filter.doFilter(request("x".repeat(65)), new MockHttpServletResponse(),
                (req, res) -> seen[0] = TraceContext.getTraceId());

        assertEquals(32, seen[0].length());
    }

    @Test
    @DisplayName("请求结束后清空日志上下文，避免线程复用串号")
    void clearsContextAfterRequest() throws Exception {
        filter.doFilter(request(null), new MockHttpServletResponse(), (req, res) -> {
        });

        assertNull(TraceContext.getTraceId());
    }

    private MockHttpServletRequest request(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/system/users");
        if (requestId != null) {
            request.addHeader(TraceContext.REQUEST_ID_HEADER, requestId);
        }
        return request;
    }
}
