package me.kirara.sable.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("租户 schema 命名规则")
class TenantSchemasTest {

    @Test
    @DisplayName("schema 名用数字 ID 拼出，不用租户 code")
    void schemaNameUsesId() {
        assertEquals("tenant_1", TenantSchemas.schemaName(1L));
        assertEquals("tenant_1001", TenantSchemas.schemaName(1001L));
    }

    @Test
    @DisplayName("search_path 里租户 schema 在前、public 在后")
    void searchPathPutsTenantFirst() {
        // 顺序反了会让业务表解析不到；漏掉 public 则 9 张公共表全部不可达
        assertEquals("tenant_7,public", TenantSchemas.searchPath(7L));
    }

    @Test
    @DisplayName("非法租户 ID 直接抛错，不生成可疑的 schema 名")
    void rejectsInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaName(null));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaName(0L));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaName(-3L));
    }

    @Test
    @DisplayName("schema 名只含小写字母、数字与下划线 —— 可以直接拼进 DDL")
    void nameIsSqlSafe() {
        String name = TenantSchemas.schemaName(123456L);
        assertTrue(name.matches("[a-z0-9_]+"), name);
    }
}
