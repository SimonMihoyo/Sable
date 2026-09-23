package me.kirara.sable.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnknown(Exception ex, HttpServletRequest request) {
        log.error("系统异常 [{}]", request.getRequestURI(), ex);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }
}
