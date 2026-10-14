package me.kirara.sable.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("全局异常处理：业务异常被持久层包装后仍要还原错误码")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/discovery/sources");

    @Test
    @DisplayName("裸的业务异常按自身错误码返回")
    void handlesPlainBusinessException() {
        R<Void> result = handler.handleBusiness(new BusinessException(ErrorCode.TENANT_CONTEXT_MISSING), request);

        assertEquals(ErrorCode.TENANT_CONTEXT_MISSING.getCode(), result.getCode());
    }

    @Test
    @DisplayName("被 MyBatis 包了两层的业务异常也能还原 —— 否则会伪装成“系统内部错误”")
    void unwrapsBusinessExceptionFromMyBatisWrapping() {
        // 真实链路：BusinessException → PersistenceException → MyBatisSystemException
        BusinessException root = new BusinessException(ErrorCode.TENANT_CONTEXT_MISSING);
        Exception persistence = new Exception("Error querying database", root);
        Exception mybatisSystem = new Exception("MyBatis system exception", persistence);

        R<Void> result = handler.handleUnknown(mybatisSystem, request);

        assertEquals(ErrorCode.TENANT_CONTEXT_MISSING.getCode(), result.getCode());
        assertEquals(ErrorCode.TENANT_CONTEXT_MISSING.getMessage(), result.getMessage());
    }

    @Test
    @DisplayName("真正的未知异常仍然返回系统内部错误")
    void keepsInternalErrorForRealFailures() {
        R<Void> result = handler.handleUnknown(new IllegalStateException("数据库连接断了"), request);

        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), result.getCode());
    }
}
