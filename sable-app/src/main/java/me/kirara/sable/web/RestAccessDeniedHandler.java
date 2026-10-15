package me.kirara.sable.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 鉴权拒绝的兜底出口 —— 写 HTTP 403 + 统一响应体。
 *
 * <p><b>正常情况下它也不会被执行。</b>{@code @PreAuthorize} 的拒绝是从 Controller 方法上
 * 抛出的，属于"进入 DispatcherServlet 之后"的异常，会被
 * {@link SecurityExceptionHandler}（一个 {@code @RestControllerAdvice}）接住并回应；
 * 异常不会穿透到 Security 的过滤器链，也就到不了这里。</p>
 *
 * <p>那为什么还要配它：不给 Security 一个"拒绝了怎么办"的答案，它就会用默认实现写一个
 * **空响应体**。也就是说，将来若有任何一条拒绝路径绕过了 MVC 的异常处理（例如以后改用
 * 在过滤器链上做 URL 级规则），响应形态会悄悄从"403 + R 体"退化成"403 + 空"，
 * 前端只能显示"请求失败（HTTP 403）"而拿不到 40801。配上是把契约钉死，成本一行。</p>
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("鉴权拒绝 [{} {}]: {}", request.getMethod(), request.getRequestURI(),
                accessDeniedException.getMessage());
        JsonResponseWriter.writeError(response, HttpServletResponse.SC_FORBIDDEN,
                ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage(), objectMapper);
    }
}
