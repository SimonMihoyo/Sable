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

    @Test
    @DisplayName("解析紧凑形态 —— 本类自身写出的那种")
    void parseCompactForm() {
        assertEquals(List.of("data_source:read", "audit_log:export"),
                JsonArrays.parseStringArray("[\"data_source:read\",\"audit_log:export\"]"));
    }

    @Test
    @DisplayName("解析 PostgreSQL jsonb 读回的规范化形态（逗号后带空格）")
    void parsePostgresForm() {
        // 这是真正会从 role.permissions 读到的样子：jsonb 输出会规范化空白
        assertEquals(List.of("a", "b", "c"), JsonArrays.parseStringArray("[\"a\", \"b\", \"c\"]"));
    }

    @Test
    @DisplayName("空数组与带空白的空数组都解析为空列表")
    void parseEmptyArray() {
        assertEquals(List.of(), JsonArrays.parseStringArray("[]"));
        assertEquals(List.of(), JsonArrays.parseStringArray("  [ ]  "));
    }

    @Test
    @DisplayName("null、空串与各种非法形态一律返回空列表，绝不抛异常")
    void parseInvalidReturnsEmpty() {
        assertEquals(List.of(), JsonArrays.parseStringArray(null));
        assertEquals(List.of(), JsonArrays.parseStringArray(""));
        assertEquals(List.of(), JsonArrays.parseStringArray("   "));
        assertEquals(List.of(), JsonArrays.parseStringArray("null"));
        assertEquals(List.of(), JsonArrays.parseStringArray("a,b"));
        assertEquals(List.of(), JsonArrays.parseStringArray("[1,2]"));
        assertEquals(List.of(), JsonArrays.parseStringArray("[{\"a\":1}]"));
        assertEquals(List.of(), JsonArrays.parseStringArray("[\"a\""));
        assertEquals(List.of(), JsonArrays.parseStringArray("[\"a\"]x"));
        assertEquals(List.of(), JsonArrays.parseStringArray("[\"a\\qz\"]"));
    }

    @Test
    @DisplayName("转义序列能还原，且与 Jackson 的解析结果逐项一致")
    void parseEscapesAgreesWithJackson() throws Exception {
        String tricky = "quote\" backslash\\ newline\ntab\t中文字符";
        String json = JsonArrays.toJsonArray(List.of(tricky));

        assertEquals(List.of(tricky), JsonArrays.parseStringArray(json));
        // 两个独立的解析实现给出同一答案，才说明手写的那份没跑偏
        assertEquals(objectMapper.readValue(json, List.class), JsonArrays.parseStringArray(json));
    }

    @Test
    @DisplayName("写到读的往返保持一致（含中文、引号与斜杠）")
    void roundTrip() {
        List<String> items = List.of("数据源:读取", "审计日志:导出", "a b/c", "带\"引号\"的权限");
        assertEquals(items, JsonArrays.parseStringArray(JsonArrays.toJsonArray(items)));
    }

    @Test
    @DisplayName("含 null 元素的数组整体判为非法 —— 不产出带 null 的权限列表")
    void nullElementIsRejectedAsWhole() {
        // 写出去是 ["a",null,"b"]。解析器只认字符串元素，遇到裸 null 即判定整个输入不符合
        // "字符串数组"形态，返回空列表。这是刻意的：与其把 null 塞进权限列表传给前端，
        // 不如整条按"配置有问题"处理（调用方会记 WARN）。
        String json = JsonArrays.toJsonArray(Arrays.asList("a", null, "b"));
        assertEquals("[\"a\",null,\"b\"]", json);
        assertEquals(List.of(), JsonArrays.parseStringArray(json));
    }
}
