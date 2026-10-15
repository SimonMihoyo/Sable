package me.kirara.sable.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 未认证时的兜底出口 —— 写 HTTP 401 + 统一响应体。
 *
 * <p><b>正常情况下它不会被执行。</b>本项目的认证由 {@link JwtAuthenticationFilter} 完成，
 * 那一步失败时自己就把 401 写掉了，链路根本走不到 Spring Security 的鉴权环节。
 * 这里存在是因为必须给 Security 一个"未认证该怎么办"的答案：用它的默认实现，
 * 响应体是**空的**（默认入口只设状态码，或跳转到登录页），
 * 而本项目的契约是"任何拒绝都带 {@code R} 体"。前端读不到错误码，
 * 就无法区分"令牌过期、刷新即可"与"服务不可用、别重试"。</p>
 *
 * <p><b>与过滤器主路径保持一致：</b>错误码同样取 {@link ErrorCode#TOKEN_INVALID}、
 * 文案同样取"缺少访问令牌"。两条路径若给出不同形态（例如这里回了 40001），
 * 前端会出现"有时能自动续期、有时突然被踢"的间歇性故障 ——
 * 而它只在"过滤器被绕过"这种罕见条件下才发生，几乎无法在现场复现。</p>
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("未认证，拒绝访问 [{} {}]: {}", request.getMethod(), request.getRequestURI(),
                authException.getMessage());
        JsonResponseWriter.writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.TOKEN_INVALID.getCode(), "缺少访问令牌", objectMapper);
    }
}
