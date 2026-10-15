package me.kirara.sable.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.TraceContext;
import me.kirara.sable.common.UserContext;
import me.kirara.sable.common.util.BearerTokens;
import me.kirara.sable.system.auth.AuthProperties;
import me.kirara.sable.system.auth.JwtPayload;
import me.kirara.sable.system.auth.JwtUtil;
import me.kirara.sable.system.service.TokenRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * JWT 认证过滤器 — 解析 {@code Authorization: Bearer <token>}，做访问拦截，
 * 并把身份、租户与权限写入各自的上下文。
 *
 * <p><b>它在链路里的位置：</b>本类<b>不是</b> Servlet 过滤器，而是挂在 Spring Security
 * 过滤器链内部（{@code addFilterBefore(..., AnonymousAuthenticationFilter.class)}，
 * 见 {@code me.kirara.sable.config.SecurityConfig}）。这一点不能含糊：Security 的
 * {@code SecurityContextHolderFilter} 会在链首把安全上下文重置为"从仓库加载"，
 * 任何在它**之前**设置进去的认证信息都会被丢掉 —— 也就是说，
 * "自己往 {@code SecurityContextHolder} 里放东西"的过滤器，必须位于安全链内部。</p>
 *
 * <p><b>拦截语义：</b>不匹配白名单的请求必须携带有效的访问令牌，否则直接以
 * HTTP 401 返回统一响应体（见 {@code R}），不进入 Controller。白名单见
 * {@link AuthProperties#getWhitelist()}，只放开确实无法携带令牌的入口。
 * Security 侧另有同名单的 {@code AuthenticationEntryPoint} 作为深防，
 * 但认证的**唯一出口**是这里。</p>
 *
 * <p><b>为什么要自己写 401 响应：</b>两点原因，缺一不可。</p>
 * <ol>
 *   <li>{@code GlobalExceptionHandler} 只对进入 DispatcherServlet 的异常生效，
 *       过滤器阶段抛出的异常到不了那里；</li>
 *   <li>调用方需要区分"令牌过期"（40002，值得去刷新）与"令牌非法"（40003，刷新也救不回来）。
 *       Security 的内置入口只能表达"未认证"这一个笼统概念。</li>
 * </ol>
 *
 * <p><b>权限的落点：</b>令牌里的 {@code perm} 声明被装配成 {@code GrantedAuthority}，
 * 供 {@code @PreAuthorize} 判断。角色**不**写进 authorities —— 鉴权一律按权限码判，
 * 理由见 {@code me.kirara.sable.common.Permission} 的类注释。因此本项目里
 * {@code hasRole(...)} 恒为假：需要"某类用户才能做"时，正确做法是给那个角色配上权限码。</p>
 *
 * <p><b>租户：</b>令牌里的 {@code tid} 是租户的唯一可信来源，本过滤器在解析成功后
 * 立即写入 {@link TenantContext}；{@link TenantContextFilter} 仅作为兜底，
 * 在令牌未携带租户（系统级用户）时才回落到 {@code X-Tenant-Id} 请求头。
 * 顺序必须早于 {@link TenantContextFilter} —— 后者要判断"租户是否已由令牌给出"；
 * 本类靠挂载点保证（安全链整体早于 {@code TenantContextFilter} 的 {@code @Order(2)}，
 * 见 {@code spring.security.filter.order}）。</p>
 *
 * <p><b>线程安全：</b>无状态，所有上下文都在 {@code finally} 里清理 ——
 * Tomcat 复用线程，不清就会串号（上一个请求的身份出现在下一个请求的日志里）。</p>
 */
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
            SecurityContextHolder.clearContext();
            UserContext.clear();
            TenantContext.clear();
            // 日志上下文里的身份同理要撤掉；traceId 不属于本过滤器，由最外层的
            // TraceIdFilter 在它自己的 finally 里清理。
            TraceContext.setUserId(null);
            TraceContext.setTenantId(null);
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

    /**
     * 把令牌中的身份、权限与租户写入上下文。
     *
     * <p>三类上下文同时写，各司其职：{@link SecurityContextHolder} 供
     * {@code @PreAuthorize} 判定"能不能做"；{@link UserContext} / {@link TenantContext}
     * 供业务代码取用；{@link TraceContext} 让本请求后续的每一行日志都带上
     * "是谁、属于哪个租户"，否则出一堆日志也答不出"这行是谁打的"。
     * 日志上下文由 {@link TraceIdFilter} 统一清理，这里只负责写入。</p>
     */
    private void fillContext(JwtPayload payload) {
        UserContext.setUserId(payload.getUserId());
        UserContext.setUsername(payload.getUsername());
        SecurityContextHolder.getContext().setAuthentication(buildAuthentication(payload));
        TraceContext.setUserId(payload.getUserId());
        if (payload.getTenantId() != null) {
            TenantContext.setTenantId(payload.getTenantId());
            TraceContext.setTenantId(payload.getTenantId());
        }
    }

    /**
     * 把令牌里的 {@code perm} 声明装配成 Security 的认证对象。
     *
     * <p>principal 用**用户名**而不是用户 ID：它会被写进
     * {@code AccessDeniedException} 的消息与拒绝日志里，可读性直接决定排查成本。
     * 需要用户 ID 的代码应当读 {@link UserContext}，不要从安全上下文里挖。</p>
     *
     * <p>credentials 传 {@code null}：令牌已经验签过了，这里既没有、也不该有
     * 可供"再次凭证校验"的东西。三参构造器会直接把状态标成"已认证"，
     * 这正是我们想表达的 —— 认证已经在令牌校验那一步完成了。</p>
     */
    private static Authentication buildAuthentication(JwtPayload payload) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String code : payload.getPermissions()) {
            // 跳过空声明：SimpleGrantedAuthority 拿到 null 会直接抛异常，
            // 于是"令牌里有个坏字段"这种配置事故会表现成 500，而不是一条清晰的日志
            if (code == null || code.isBlank()) {
                continue;
            }
            authorities.add(new SimpleGrantedAuthority(code));
        }
        return new UsernamePasswordAuthenticationToken(payload.getUsername(), null, authorities);
    }

    /** 认证失败：写 HTTP 401 + 统一响应体，链路到此为止。 */
    private void reject(HttpServletResponse response, int code, String message) throws IOException {
        log.warn("认证失败，拒绝访问: {} code={}", message, code);
        JsonResponseWriter.writeError(response, HttpServletResponse.SC_UNAUTHORIZED, code, message,
                objectMapper);
    }
}
