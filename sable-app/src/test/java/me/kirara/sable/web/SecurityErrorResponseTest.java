package me.kirara.sable.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.R;
import me.kirara.sable.common.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拒绝响应契约测试 —— 把"任何一次拒绝都必须是<b>真实 HTTP 状态码 + 统一 {@code R} 体</b>"
 * 这一条钉住，而不是逐个类去测。
 *
 * <p>钉在"契约"上而不是"类"上，是因为同一条契约由三处分别实现，而它们各自的默认行为
 * 都是<b>空响应体</b>：</p>
 * <ol>
 *   <li>{@link RestAuthenticationEntryPoint} —— Spring Security 默认只设状态码，不写 body；</li>
 *   <li>{@link RestAccessDeniedHandler} —— 同上；</li>
 *   <li>{@link SecurityExceptionHandler} —— 不写它，异常会被
 *       {@code GlobalExceptionHandler} 的兜底吞成「系统内部错误」。</li>
 * </ol>
 * <p>三处只要漏一处，对应的那条路径上响应体就会变空 —— 而前端读不到 {@code code}
 * 就无法区分"令牌过期可以刷新"与"服务不可用"。分开写测试的话，
 * 很容易出现"三处里只测了两处、漏的那个正好是真实触发的那一个"。</p>
 */
class SecurityErrorResponseTest {

    private static final String PROTECTED_URI = "/system/roles";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ------------------------------------------------------------------ 401

    /**
     * 正常路径上这条兜底<b>不会</b>被执行（认证失败由 {@code JwtAuthenticationFilter} 自己写响应）。
     * 但它的形态必须与过滤器主路径一致 —— 否则一旦这条路径真的生效，
     * 前端的自动续期就会莫名其妙地失效。
     */
    @Test
    @DisplayName("未认证兜底：401 + 40003，且响应体与过滤器主路径同形")
    void entryPointWritesUnauthorized() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(objectMapper).commence(
                new MockHttpServletRequest("GET", PROTECTED_URI), response,
                new BadCredentialsException("no token"));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        JsonNode body = body(response);
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), body.get("code").asInt());
        assertEquals("缺少访问令牌", body.get("message").asText());
        assertTrue(response.getContentType().contains("UTF-8"),
                "响应需显式声明 UTF-8，否则中文错误消息会乱码：" + response.getContentType());
    }

    // ------------------------------------------------------------------ 403

    @Test
    @DisplayName("鉴权拒绝兜底：403 + 40801")
    void accessDeniedHandlerWritesForbidden() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(objectMapper).handle(
                new MockHttpServletRequest("GET", PROTECTED_URI), response,
                new AccessDeniedException("denied"));

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        JsonNode body = body(response);
        assertEquals(ErrorCode.FORBIDDEN.getCode(), body.get("code").asInt());
        assertEquals(ErrorCode.FORBIDDEN.getMessage(), body.get("message").asText());
    }

    /**
     * {@code @PreAuthorize} 拒绝走的就是这一条 —— 它是实际会被执行到的那个分支。
     *
     * <p>没有它，{@code AuthorizationDeniedException} 会落进
     * {@code GlobalExceptionHandler.handleUnknown(Exception)}，
     * 一次明确的"你没这个权限"就被伪装成「系统内部错误」，前后端排查方向全错。</p>
     */
    @Test
    @DisplayName("@PreAuthorize 拒绝经 advice 转成 40801，而不是被兜底成「系统内部错误」")
    void accessDeniedBecomesBusinessForbidden() {
        UserContext.setUsername("bob");

        R<Void> result = new SecurityExceptionHandler().handleAccessDenied(
                new AccessDeniedException("Access Denied"),
                new MockHttpServletRequest("GET", PROTECTED_URI));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), result.getCode());
        assertEquals(ErrorCode.FORBIDDEN.getMessage(), result.getMessage());
    }

    /**
     * HTTP 状态码由 {@code @ResponseStatus} 决定 —— 方法返回值本身不带状态码。
     *
     * <p>漏掉这个注解的表现很隐蔽：接口仍回 {@code {code:40801}}，前端也能提示"无权限"，
     * 但 HTTP 状态是 200，于是任何按状态码判断的工具（网关、监控、客户端的重试策略）
     * 都会把它当成一次成功的调用。</p>
     */
    @Test
    @DisplayName("advice 必须带 @ResponseStatus(FORBIDDEN)，否则拒绝会以 HTTP 200 返回")
    void accessDeniedCarriesForbiddenStatus() throws Exception {
        ResponseStatus status = AnnotationUtils.findAnnotation(
                SecurityExceptionHandler.class.getMethod("handleAccessDenied",
                        AccessDeniedException.class, HttpServletRequest.class),
                ResponseStatus.class);

        assertNotNull(status, "handleAccessDenied 缺少 @ResponseStatus");
        assertEquals(HttpStatus.FORBIDDEN, status.value());
    }

    /**
     * 两个 advice 同时在容器里时，Spring 取第一个"有匹配处理方法"的。
     * {@code GlobalExceptionHandler} 未标顺序（等价于最低优先级），
     * 本类必须显式排到它前面，否则上面那条断言在真实请求里不成立。
     */
    @Test
    @DisplayName("本 advice 的 @Order 必须高于 GlobalExceptionHandler 的兜底")
    void adviceOutranksGlobalHandler() {
        Integer order = OrderUtils.getOrder(SecurityExceptionHandler.class);

        assertNotNull(order, "SecurityExceptionHandler 缺少 @Order");
        assertEquals(Integer.valueOf(Ordered.HIGHEST_PRECEDENCE), order);
    }

    // ------------------------------------------------------------------ 辅助

    private JsonNode body(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }
}
