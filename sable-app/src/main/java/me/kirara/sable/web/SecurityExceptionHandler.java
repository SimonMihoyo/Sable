package me.kirara.sable.web;

import jakarta.servlet.http.HttpServletRequest;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.R;
import me.kirara.sable.common.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Security 的鉴权异常 → 统一响应体。
 *
 * <p><b>为什么必须有它：</b>{@code @PreAuthorize} 拒绝时抛的是
 * {@code AuthorizationDeniedException}（{@link AccessDeniedException} 的子类）。
 * 它从 Controller 方法上抛出 —— 也就是在 DispatcherServlet 之内 —— 于是：
 * 既走不到 Security 的 {@code AccessDeniedHandler}（那个在过滤器链上，在 Servlet 之外），
 * 也**没有**被 {@code GlobalExceptionHandler} 单独列出，最终会被它的兜底
 * {@code handleUnknown(Exception)} 吞成「系统内部错误」。一次明确的"你没这个权限"
 * 就这样被伪装成服务端故障，前端与日志都指向错误的方向。</p>
 *
 * <p><b>为什么要显式排在前面：</b>两个 {@code @RestControllerAdvice} 同时存在时，
 * Spring 按 {@code @Order} 顺序取第一个"有匹配处理方法"的。
 * {@code GlobalExceptionHandler} 未标 {@code @Order}（等价于最低优先级），
 * 所以这里标 {@code HIGHEST_PRECEDENCE} 才能先拿到 {@code AccessDeniedException}；
 * 其余异常照旧落回它那里，本类不改变任何既有行为。</p>
 *
 * <p><b>HTTP 状态码取 403、业务码取 40801</b>，与架构文档 4.3 节"拒绝 → 403"一致，
 * 也与 400（参数非法）/ 404（路径不存在）/ 405（方法不支持）这几个同类客户端错误的既有
 * 处理方式一致：它们都是**真实 HTTP 状态码 + {@code R} 体**。所谓"业务失败走 HTTP 200"
 * 指的是服务端已受理但业务上不成功，而"你没这个权限"是调用方用错了入口，不是业务结果。</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        // 记在 WARN：这是"有人试图访问他没有权限的接口"，属于安全事件的可观测入口。
        // 消息里带上用户与路径，排查时不必再去日志里翻前后文。
        log.warn("鉴权拒绝 [{} {}] 用户={} : {}", request.getMethod(), request.getRequestURI(),
                UserContext.getUsername(), ex.getMessage());
        return R.fail(ErrorCode.FORBIDDEN);
    }
}
