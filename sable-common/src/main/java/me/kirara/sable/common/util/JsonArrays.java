package me.kirara.sable.common.util;

import java.util.List;

/**
 * JSON 字符串数组的最小序列化工具 —— 供「以字符串承载 JSONB」的列写库时使用。
 *
 * <p><b>为什么手写而不引 Jackson：</b>库模块声明的 {@code spring-boot-starter-web} 是
 * {@code provided} scope，而 <b>{@code provided} 的传递依赖不进编译 classpath</b>，
 * 因此 Jackson 在库模块里取不到（见根 {@code AGENTS.md} 第 2 节）。</p>
 *
 * <p><b>为什么必须用它：</b>PostgreSQL 的 {@code JSONB} 列只接受合法 JSON 文本。
 * 直接拼接（例如 {@code String.join(",", ids)}）得到的是 {@code a,b} 这种既非 JSON
 * 也无从解析的字符串，写库时会被数据库以 {@code invalid input syntax for type json} 拒绝。
 * 本类负责把 {@code List<String>} 转成 {@code ["a","b"]} 形式的字面量，并转义
 * 引号、反斜杠与控制字符。</p>
 *
 * <p><b>不含反序列化：</b>从 {@code JSONB} 列读回时，数据库输出的已是规范 JSON 文本
 * （形如 {@code ["a", "b"]}，逗号后带空格），需要解析的场景自行处理。</p>
 *
 * <p>典型用途：{@code role.permissions}、{@code alert_rule.notify_channels} 等 JSONB 列。</p>
 */
public final class JsonArrays {

    private JsonArrays() {
    }

    /**
     * 把字符串列表序列化为 JSON 数组字面量。
     *
     * @param items 元素列表，可为 {@code null} 或含 {@code null} 元素
     *              （{@code null} 元素输出为 JSON 的 {@code null} 而非字符串 {@code "null"}）
     * @return 合法 JSON 数组文本；入参为 {@code null} 时返回 {@code []}
     */
    public static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                appendElement(sb, items.get(i));
            }
        }
        return sb.append(']').toString();
    }

    /**
     * 追加一个数组元素：{@code null} 输出 JSON 的 {@code null}，否则输出带转义的 JSON 字符串。
     *
     * <p>转义覆盖 JSON 规范要求的两类字符：必须转义的 {@code "} 与 {@code \}，
     * 以及 {@code U+0000}–{@code U+001F} 的控制字符（其中常见者走短转义，
     * 其余走 {@code \\uXXXX}）。</p>
     */
    private static void appendElement(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
