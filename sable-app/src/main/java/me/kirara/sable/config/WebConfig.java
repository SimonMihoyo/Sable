package me.kirara.sable.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web 层通用配置 —— 跨域（CORS）。
 *
 * <p><b>为什么必须有：</b>前端是独立的 React 工程（架构文档 5.1 节把 dev server 定在 3000），
 * 与后端 8080 不同源。浏览器对跨源请求先发预检、再校验响应头，服务端不声明
 * {@code Access-Control-Allow-*} 时请求根本到不了 Controller，前端只能看到一句笼统的
 * CORS 报错。此前全项目没有任何跨域配置，联调无法起步。</p>
 *
 * <p><b>为什么同时用了两种注册方式：</b>它们覆盖的是链路上先后两段，缺一段就会留下破口。</p>
 * <ol>
 *   <li>{@link #addCorsMappings} —— 规则声明在 MVC 层，负责预检应答与 Controller 响应头；</li>
 *   <li>{@link #corsFilter} —— 把同一份规则提前到过滤器链最前端（{@code HIGHEST_PRECEDENCE}）。
 *       这一层是必需的：MVC 的跨域处理发生在 DispatcherServlet 之后，而
 *       {@code JwtAuthenticationFilter} 会在它之前就直接写出 401。
 *       只靠第 1 条时，实测「不带令牌的请求」返回的 401 **不带任何跨域头**，
 *       浏览器会把「令牌过期，请重新登录」屏蔽成不透明的 CORS 错误 ——
 *       前端既读不到错误码，也无法区分"令牌失效"与"服务不可用"。</li>
 * </ol>
 *
 * <p><b>允许源：</b>默认值是开发期的宽松值（localhost 任意端口）。写死端口几乎必然对不上 ——
 * 文档写 3000，而 Umi 4 脚手架默认 8000、Vite 默认 5173。
 * 生产环境务必通过 {@code SABLE_CORS_ALLOWED_ORIGIN_PATTERNS} 覆盖为真实域名。
 * 两种注册方式共用同一个字段，不存在配置漂移。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 允许源的默认值，仅适用于本地开发；生产必须覆盖。 */
    private static final String DEV_ORIGIN_PATTERNS =
            "http://localhost:[*],http://127.0.0.1:[*]";

    /** 预检结果的缓存秒数，避免每个真实请求都先跟一次预检。 */
    private static final long PREFLIGHT_MAX_AGE = 3600L;

    private static final String[] ALLOWED_METHODS =
            {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"};

    /**
     * 允许的请求头。
     *
     * <p>刻意用白名单而非 {@code *}：{@code Authorization} 是令牌入口，
     * {@code X-Tenant-Id} 是 {@code TenantContextFilter} 读取的租户覆盖项，
     * 两者少一个都会让前端在预检阶段就被拒。</p>
     */
    private static final String[] ALLOWED_HEADERS =
            {"Authorization", "Content-Type", "Accept", "X-Tenant-Id", "X-Requested-With"};

    private final List<String> allowedOriginPatterns;

    public WebConfig(@Value("${sable.web.cors.allowed-origin-patterns:" + DEV_ORIGIN_PATTERNS + "}")
                     List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    /**
     * 在 MVC 层声明跨域规则。
     *
     * <p>{@code allowCredentials} 保持关闭：本项目用请求头里的 Bearer 令牌做认证，
     * 不依赖 Cookie，浏览器无需携带凭证。关闭也顺带避免了"通配源 + 允许凭证"
     * 这一被浏览器明确禁止的组合。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]))
                .allowedMethods(ALLOWED_METHODS)
                .allowedHeaders(ALLOWED_HEADERS)
                .allowCredentials(false)
                .maxAge(PREFLIGHT_MAX_AGE);
    }

    /**
     * 把同一份跨域规则提前到过滤器链最前端。
     *
     * <p>作用见类注释第 2 点：让过滤器阶段产生的响应（典型就是 JWT 认证失败的 401）
     * 也带上跨域头，前端才能读到 {@code R} 里的错误码。
     * 顺序必须早于 {@code JwtAuthenticationFilter}（其 {@code @Order(1)}），
     * 这里用 {@code HIGHEST_PRECEDENCE} 显式压到最前。</p>
     *
     * <p>用 {@link FilterRegistrationBean} 而不是裸的 {@code @Bean CorsFilter}：
     * 前者的 {@code setOrder} 对容器可靠生效，后者的顺序依赖注解且易被其它
     * 过滤器的注册顺序盖过。</p>
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of(ALLOWED_METHODS));
        configuration.setAllowedHeaders(List.of(ALLOWED_HEADERS));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(PREFLIGHT_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
