package me.kirara.sable.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.R;
import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.UserContext;
import me.kirara.sable.common.util.BearerTokens;
import me.kirara.sable.system.auth.AuthProperties;
import me.kirara.sable.system.auth.JwtPayload;
import me.kirara.sable.system.auth.JwtUtil;
import me.kirara.sable.system.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器 — 解析 {@code Authorization: Bearer <token>}，做访问拦截，
 * 并把身份与租户写入上下文。
 *
 * <p><b>拦截语义：</b>不匹配白名单的请求必须携带有效的访问令牌，否则直接以
 * HTTP 401 返回统一响应体（{@link R}），不进入 Controller。白名单见
 * {@link AuthProperties#getWhitelist()}，只放开确实无法携带令牌的入口。</p>
 *
 * <p><b>为什么在过滤器里自己写响应：</b>{@code GlobalExceptionHandler} 只对进入
 * DispatcherServlet 的异常生效，过滤器阶段抛出的异常到不了那里。这里手工序列化
 * {@link R}，语义与全局处理器保持一致。</p>
 *
 * <p><b>租户：</b>令牌里的 {@code tid} 是租户的唯一可信来源，本过滤器在解析成功后
 * 立即写入 {@link TenantContext}；{@link TenantContextFilter} 仅作为兜底，
 * 在令牌未携带租户（系统级用户）时才回落到 {@code X-Tenant-Id} 请求头。</p>
 *
 * <p>顺序必须早于 {@link TenantContextFilter} —— 后者要判断"租户是否已由令牌给出"。</p>
 */
@Component
@Order(1)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证请求头名，转引自 {@link BearerTokens} 以便调用方少一个 import。 */
    public static final String AUTH_HEADER = BearerTokens.HEADER;

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final TokenRevocationService tokenRevocationService;
    private final AuthProperties authProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenRevocationService tokenRevocationService,
                                   AuthProperties authProperties, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.tokenRevocationService = tokenRevocationService;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (isWhitelisted(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = BearerTokens.resolve(request.getHeader(AUTH_HEADER));
            if (token == null) {
                reject(response, ErrorCode.TOKEN_INVALID.getCode(), "缺少访问令牌");
                return;
            }

            JwtPayload payload;
            try {
                payload = jwtUtil.parse(token);
            } catch (BusinessException e) {
                // parse 内部已区分「过期」（40002）与「非法」（40003），直接沿用
                reject(response, e.getCode(), e.getMessage());
                return;
            }

            if (!JwtUtil.TYPE_ACCESS.equals(payload.getType())) {
                reject(response, ErrorCode.TOKEN_INVALID.getCode(), "令牌类型不正确");
                return;
            }

            if (tokenRevocationService.isRevoked(payload.getJti())) {
                reject(response, ErrorCode.TOKEN_INVALID.getCode(), "令牌已被吊销");
                return;
            }

            fillContext(payload);
            filterChain.doFilter(request, response);
        } finally {
            // 线程池会复用线程，不清会串号
            UserContext.clear();
            TenantContext.clear();
        }
    }

    /** 路径是否命中白名单。 */
    private boolean isWhitelisted(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : authProperties.getWhitelist()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** 把令牌中的身份与租户写入上下文。 */
    private void fillContext(JwtPayload payload) {
        UserContext.setUserId(payload.getUserId());
        UserContext.setUsername(payload.getUsername());
        if (payload.getTenantId() != null) {
            TenantContext.setTenantId(payload.getTenantId());
        }
    }

    /** 认证失败：写 HTTP 401 + 统一响应体，链路到此为止。 */
    private void reject(HttpServletResponse response, int code, String message) throws IOException {
        log.warn("认证失败，拒绝访问: {} code={}", message, code);
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 显式声明 UTF-8，否则响应头不带 charset，客户端可能按本地编码解码导致中文乱码
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), R.fail(code, message));
    }
}
