package me.kirara.sable.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("租户上下文：「缺租户」的判定只有唯一出口，且固定报 40104")
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("有租户时原样返回")
    void returnsTenantIdWhenPresent() {
        TenantContext.setTenantId(7L);

        assertEquals(7L, TenantContext.requireTenantId().longValue());
    }

    @Test
    @DisplayName("没租户时抛 40104 —— 不是 40101（那是「租户不存在」，语义不同）")
    void throwsContextMissingWhenAbsent() {
        BusinessException ex = assertThrows(BusinessException.class, TenantContext::requireTenantId);

        assertEquals(ErrorCode.TENANT_CONTEXT_MISSING.getCode(), ex.getCode());
        assertEquals(ErrorCode.TENANT_CONTEXT_MISSING.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("clear 之后重新变回「没有租户」—— 防线程复用串租户")
    void clearResetsContext() {
        TenantContext.setTenantId(7L);

        TenantContext.clear();

        assertNull(TenantContext.getTenantId());
        assertThrows(BusinessException.class, TenantContext::requireTenantId);
    }
}
