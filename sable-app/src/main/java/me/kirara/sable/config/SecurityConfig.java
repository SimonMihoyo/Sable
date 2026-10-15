package me.kirara.sable.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.kirara.sable.system.auth.AuthProperties;
import me.kirara.sable.system.auth.JwtUtil;
import me.kirara.sable.system.service.TokenRevocationService;
import me.kirara.sable.web.JwtAuthenticationFilter;
import me.kirara.sable.web.RestAccessDeniedHandler;
import me.kirara.sable.web.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 接口级鉴权装配 —— 只引入 Spring Security 的"鉴权"那一半。
 *
 * <p><b>分工：</b>认证（你是谁）仍由本项目的 {@link JwtAuthenticationFilter} 完成，
 * 鉴权（你能做什么）交给 {@code @PreAuthorize} 与方法级的
 * {@code AuthorizationManager}。之所以不把认证也交给 Security，是因为认证失败的响应
 * 需要区分 40001 / 40002 / 40003 —— Security 的内置入口只能表达"未认证"一个笼统概念，
 * 而前端要靠 40002 决定"去刷新令牌"还是"回登录页"。</p>
 *
 * <p><b>装配这个类时必须同时照顾四件事，改动前请逐条确认：</b></p>
 * <ol>
 *   <li><b>认证过滤器必须挂在安全链<b>内部</b></b>（见 {@link #jwtAuthenticationFilter} 与
 *       下面的 {@code addFilterBefore}）。Security 的 {@code SecurityContextHolderFilter}
 *       在链首把安全上下文重置为"从仓库加载"，任何在它之前写进去的认证信息都会被清掉。</li>
 *   <li><b>不能启用 {@code .cors()}</b>。跨域已由 {@link WebConfig} 双注册
 *       （MVC 层 + 压到 {@code HIGHEST_PRECEDENCE} 的 {@code CorsFilter}）负责。
 *       再让 Security 处理一遍会多出第二个 {@code CorsFilter}，
 *       响应里出现**重复的** {@code Access-Control-Allow-Origin}，
 *       浏览器按"多值"直接拒收 —— 这比完全没有跨域头更难排查，
 *       因为它只在带预检的真实跨源请求上出现。</li>
 *   <li><b>过滤器顺序</b>：安全链整体必须夹在 {@code TraceIdFilter}（{@code @Order(0)}）
 *       与 {@code TenantContextFilter}（{@code @Order(2)}）之间，由
 *       {@code spring.security.filter.order=1} 指定（见 {@code application.yml}）。
 *       默认值 {@code -100} 会让安全链跑到 {@code TraceIdFilter} 之前，
 *       于是认证失败的 401 不带 traceId、也不回写 {@code X-Request-Id}。</li>
 *   <li><b>响应契约</b>：任何拒绝都必须是"真实 HTTP 状态码 + {@code R} 体"，
 *       由 {@link RestAuthenticationEntryPoint}、{@link RestAccessDeniedHandler} 与
 *       {@code SecurityExceptionHandler} 三处共同保证。缺任何一处，
 *       对应路径上的响应体就会变成空的。</li>
 * </ol>
 *
 * <p><b>为什么 {@code anyRequest().authenticated()} 与过滤器里的白名单判断重复：
 * 这是有意的深防。</b>过滤器那边判断白名单，是为了给出细分错误码（主路径）；
 * 这里再判一次，是为了让"将来有人改坏了过滤器"不至于直接变成接口裸奔 ——
 * 那时未认证请求会拿到一个 401 + {@code R} 体，而不是数据。</p>
 *
 * <p><b>未做、也不要顺手做的两件事：</b></p>
 * <ul>
 *   <li>没启用 {@code methodSecurity} 之外的 URL 级权限规则（例如
 *       {@code requestMatchers("/system/**").hasAuthority(...)}）。
 *       URL 规则与 {@code @PreAuthorize} 并存时，"哪个接口要哪个码"会散落两处，
 *       而无注解的接口又会被 URL 规则默默覆盖 —— 定位权限问题时得同时看两边。
 *       本项目统一只用 {@code @PreAuthorize}，写在哪里、一眼可查。</li>
 *   <li>没有引入 {@code AuthenticationManager} / {@code UserDetailsService}：
 *       登录是本项目自己的 {@code AuthServiceImpl}，不走 Security 的认证流程。
 *       对应的自动配置已在 {@code application.yml} 里显式排除
 *       （否则启动日志会打印一个用不到的随机密码，而那确实是一个能登录的账号）。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 认证过滤器 —— 刻意<b>不</b>标 {@code @Component}。
     *
     * <p>它必须只执行一次，且只在安全链内部执行。标了 {@code @Component}，Spring Boot 会
     * 把它**同时**注册成 Servlet 过滤器，于是每个请求会解析两遍令牌、判定两遍白名单；
     * 更糟的是那一次执行发生在安全链之外，写进安全上下文的东西随后会被
     * {@code SecurityContextHolderFilter} 清掉 —— 表现为"过滤器明明放行了，接口却仍是 401"。
     * 用 {@code new} 构造、只交给 {@code addFilterBefore} 挂载，就没有这条岔路。</p>
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil,
                                                          TokenRevocationService tokenRevocationService,
                                                          AuthProperties authProperties,
                                                          ObjectMapper objectMapper) {
        return new JwtAuthenticationFilter(jwtUtil, tokenRevocationService, authProperties, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthProperties authProperties,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // 无状态 + 请求头里的 Bearer 令牌：浏览器不会自动携带凭证，CSRF 攻击的前提
                // （Cookie 被自动附加上）不成立。不关掉的话，所有 POST / PUT / DELETE
                // 都会因为没有 CSRF token 而被拒成 403 —— 且错误信息与"没权限"长得一样，
                // 极易被误诊成权限配置问题。
                .csrf(AbstractHttpConfigurer::disable)
                // 以下三项都是"用不到、但默认打开"的：表单登录页、HTTP Basic 弹窗、
                // Security 自带的 /logout。本项目的登出是 /auth/logout —— 它会真的吊销令牌
                // （见 AuthServiceImpl.logout 与 token_revocation 表），而不是清一个并不存在的会话。
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 不启用 .cors()，理由见类注释第 2 条
                .authorizeHttpRequests(auth -> auth
                        // 白名单取自同一份配置（sable.auth.whitelist），避免两处各写一份而漂移。
                        // 注意该键一旦在 yml 里出现就会**整体覆盖**代码里的默认值，改配置时要列全。
                        .requestMatchers(authProperties.getWhitelist().toArray(new String[0])).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 挂载点：在所有"身份已建立"的内置过滤器之后、AnonymousAuthenticationFilter 之前。
                // 选这个位置的两个理由 —— 必须晚于 SecurityContextHolderFilter（它会重置上下文），
                // 并且必须早于 AuthorizationFilter（后者要读我们写进去的认证信息）。
                .addFilterBefore(jwtAuthenticationFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }
}
