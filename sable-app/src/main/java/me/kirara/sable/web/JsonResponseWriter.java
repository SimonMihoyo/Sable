package me.kirara.sable.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import me.kirara.sable.common.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * 过滤器 / 安全链阶段写统一响应体 —— 把 {@link R} 序列化成 JSON 回给调用方。
 *
 * <p><b>为什么过滤器要自己写响应：</b>{@code GlobalExceptionHandler} 是
 * {@code @RestControllerAdvice}，只对"已经进入 DispatcherServlet"的异常生效。
 * 过滤器与安全链在它之前就结束了链路，异常到不了那里；若不在这一层手工序列化，
 * 调用方只会拿到一个空体或容器的 HTML 错误页 —— 前端读不到错误码，
 * 也就无从区分"令牌过期，刷新即可"与"服务不可用，别重试"。</p>
 *
 * <p><b>为什么要抽成一个类：</b>现在有三处要写同样的东西 —— 认证过滤器、
 * Spring Security 的 {@code AuthenticationEntryPoint} 与 {@code AccessDeniedHandler}。
 * 各写一份的代价是"某天有人只改了其中一处的 charset"，
 * 于是中文错误消息在某个分支上偶发乱码，且极难复现。</p>
 */
final class JsonResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(JsonResponseWriter.class);

    private JsonResponseWriter() {
    }

    /**
     * 以指定的 HTTP 状态码写出 {@code R.fail(code, message)}。
     *
     * @param httpStatus     HTTP 状态码（认证失败 401、鉴权拒绝 403）
     * @param code           业务错误码，见 {@code ErrorCode}
     * @param message        错误消息
     * @param objectMapper   序列化器（容器里那一个，保证与接口响应同样的字段策略）
     * @throws IOException 写出失败（客户端已断开等），由调用方所在容器处理
     */
    static void writeError(HttpServletResponse response, int httpStatus, int code, String message,
                           ObjectMapper objectMapper) throws IOException {
        if (response.isCommitted()) {
            // 已经有人写过响应体了。此时再 setStatus / 写 body 会抛 IllegalStateException，
            // 把一个"重复拒绝"升级成 500，反而掩盖掉先写出去的真正原因。
            log.warn("响应已提交，跳过写入统一响应体 httpStatus={} code={}", httpStatus, code);
            return;
        }
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 显式声明 UTF-8。缺省时 application/json 不带 charset，部分客户端会按本地
        // 默认编码（如 GBK）解码，于是错误消息里的中文变成乱码 —— 而错误消息恰恰是最需要看懂的部分。
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), R.fail(code, message));
    }
}
