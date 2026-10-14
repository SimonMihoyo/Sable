package me.kirara.sable.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link JsonArrays} 的序列化与转义测试。
 *
 * <p>核心断言只有一个：**产出的文本必须是合法 JSON 数组**。因为下游是 PostgreSQL 的
 * {@code JSONB} 列，格式非法会被数据库直接拒绝（{@code invalid input syntax for type json}），
 * 而不是静默存成脏数据。</p>
 *
 * <p>校验用 Jackson 做裁判 —— 被测类本身<b>不依赖</b> Jackson（库模块的
 * {@code spring-boot-starter-web} 是 {@code provided}，传递依赖取不到），
 * 但测试 classpath 有（{@code spring-boot-starter-test} 带入），正好用来做独立验证：
 * 生成与解析由两套代码分别完成，不会自说自话。</p>
 */
class JsonArraysTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("空列表与 null 都产出空数组")
    void emptyArray() {
        assertEquals("[]", JsonArrays.toJsonArray(List.of()));
        assertEquals("[]", JsonArrays.toJsonArray(null));
    }

    @Test
    @DisplayName("普通权限标识按 JSON 数组输出")
    void plainElements() {
        assertEquals("[\"data_source:read\",\"audit_log:export\"]",
                JsonArrays.toJsonArray(List.of("data_source:read", "audit_log:export")));
    }

    @Test
    @DisplayName("引号、反斜杠、换行、制表、退格与不可见控制字符均正确转义")
    void escapesSpecialCharacters() throws Exception {
        String tricky = "quote\" backslash\\ newline\ntab\tcr\rbackspace\bformfeed\fbell\u0007";
        String json = JsonArrays.toJsonArray(List.of(tricky));

        // 能被独立解析回原值，才说明转义是对的
        assertEquals(List.of(tricky), objectMapper.readValue(json, List.class));
    }

    @Test
    @DisplayName("null 元素输出 JSON 的 null，而不是字符串 \"null\"")
    void nullElement() throws Exception {
        String json = JsonArrays.toJsonArray(Arrays.asList("a", null, "b"));

        assertEquals("[\"a\",null,\"b\"]", json);
        assertEquals(Arrays.asList("a", null, "b"), objectMapper.readValue(json, List.class));
    }

    @Test
    @DisplayName("含中文与特殊符号的元素不被破坏")
    void keepsUnicodeIntact() throws Exception {
        List<String> items = List.of("数据源:读取", "审计日志:导出", "a b/c");
        String json = JsonArrays.toJsonArray(items);

        assertEquals(items, objectMapper.readValue(json, List.class));
    }
}
