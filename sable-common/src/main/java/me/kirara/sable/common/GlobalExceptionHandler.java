package me.kirara.sable.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器 — 将异常统一转换为 {@link R} 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常 [{}] {}: {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        return R.fail(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return R.fail(ErrorCode.PARAM_INVALID.getCode(), message);
    }

    /**
     * 路径不存在 —— Spring Boot 3.2+ 对没有匹配到任何 Controller / 静态资源的请求
     * 会抛出 {@link NoResourceFoundException}。
     * <p>必须单独处理：否则会被下面的 {@link #handleUnknown} 兜底，导致所有 404
     * （首页、favicon、写错的接口路径）都变成 HTTP 200 + 「系统内部错误」，掩盖真实问题。
     * 这里按客户端路径错误处理：返回 404，且不打堆栈、不记 ERROR 日志。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在 [{}] {}", request.getMethod(), request.getRequestURI());
        return R.fail(ErrorCode.NOT_FOUND);
    }

    /**
     * 请求方法不匹配 —— 路径存在但 HTTP 方法不对（例如对 {@code POST /auth/login} 发 GET）。
     * <p>同 {@link #handleNotFound}：必须单独处理，否则会被兜底handler吞成
     * 「系统内部错误」，让客户端的调用方式错误看起来像服务端故障。</p>
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                           HttpServletRequest request) {
        log.warn("请求方法不支持 [{}] {} —— 支持: {}", ex.getMethod(), request.getRequestURI(),
                ex.getSupportedHttpMethods());
        return R.fail(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 请求体无法解析 —— 典型场景是请求体不是合法 JSON（如手写 JSON 少个引号）。
     * <p>属客户端错误，复用 {@link ErrorCode#PARAM_INVALID}，不新增错误码。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("请求体解析失败 [{}] {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return R.fail(ErrorCode.PARAM_INVALID.getCode(), "请求体格式错误");
    }

    /**
     * 请求 Content-Type 不受支持 —— 例如把认证头误写进了 Content-Type。
     * <p>同属客户端错误，必须单独处理，否则会被兜底成「系统内部错误」。</p>
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public R<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                               HttpServletRequest request) {
        log.warn("媒体类型不支持 [{}] {} —— Content-Type: {}", request.getMethod(),
                request.getRequestURI(), ex.getContentType());
        return R.fail(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknown(Exception ex, HttpServletRequest request) {
        // 持久层里抛出的业务异常会被 MyBatis 逐层包装
        // （BusinessException → PersistenceException → MyBatisSystemException），
        // 直接兜底就会把一次明确的业务拒绝伪装成「系统内部错误」，让调用方无从判断。
        // 因此先沿异常链找 BusinessException，找到就按它自己的错误码回应。
        BusinessException business = businessCause(ex);
        if (business != null) {
            return handleBusiness(business, request);
        }
        log.error("系统异常 [{}]", request.getRequestURI(), ex);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * 沿异常链查找 {@link BusinessException}。
     *
     * <p>限制层数是为了防御性地避免异常链异常（自引用）造成的死循环 —— 正常情况下
     * 包装不会超过三层。</p>
     */
    private static BusinessException businessCause(Throwable ex) {
        Throwable current = ex;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof BusinessException business) {
                return business;
            }
            current = current.getCause();
        }
        return null;
    }
}
