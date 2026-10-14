package me.kirara.sable.common.util;

import java.util.ArrayList;
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
 * <p><b>读写成对：</b>从 {@code JSONB} 列读回时，数据库输出的是规范 JSON 文本（形如
 * {@code ["a", "b"]}，逗号后带空格）。{@link #parseStringArray(String)} 负责反向解析，
 * 与 {@link #toJsonArray(List)} 构成一对。手写而不引 Jackson 的理由同上（库模块编译期
 * 取不到 Jackson；{@code sable-system} 里 {@code jjwt-jackson} 是 {@code runtime} scope，
 * 同样不进编译 classpath）。</p>
 *
 * <p>解析器<b>只支持字符串数组</b>这一种形态 —— 它本就是本类写出去的东西。
 * 遇到任何不符合该形态的输入（嵌套对象、数字元素、非法转义、未闭合）一律返回空列表，
 * 不抛异常：调用方多为"装配权限"，让一个坏掉的角色配置把整个请求打成 500 并不划算。</p>
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

    /**
     * 把 JSON 字符串数组字面量解析为列表 —— {@link #toJsonArray(List)} 的逆操作。
     *
     * <p>接受的形态即本类写出去的东西，另加 PostgreSQL {@code jsonb} 读回时的规范化形态
     * （元素间带空格）：{@code []}、{@code ["a","b"]}、{@code ["a", "b"]}。</p>
     *
     * @param json JSON 数组文本，可为 {@code null}
     * @return 解析结果；输入为 {@code null}、空串或任何非法形态时返回<b>空列表</b>，
     *         绝不返回 {@code null}，也不抛异常（理由见类注释）
     */
    public static List<String> parseStringArray(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return doParse(json);
        } catch (RuntimeException ex) {
            // 仅在输入畸形时到达（例如十六进制转义里出现非十六进制字符）。
            // 刻意不向上抛：见类注释中"坏配置不该把请求打成 500"的说明。
            return List.of();
        }
    }

    /**
     * 实际的解析过程。任何不符合"字符串数组"形态的输入都以返回空列表表达，
     * 与 {@link #parseStringArray(String)} 的成功返回值无法区分 —— 这是有意的简化，
     * 调用方若需要区分，应自行判断入参是否为空白串。
     */
    private static List<String> doParse(String json) {
        final int n = json.length();
        int i = skipWhitespace(json, 0);

        if (i >= n || json.charAt(i) != '[') {
            return List.of();
        }
        i++;

        List<String> items = new ArrayList<>();
        while (true) {
            i = skipWhitespace(json, i);
            if (i >= n) {
                return List.of();
            }
            char c = json.charAt(i);
            if (c == ']') {
                i++;
                break;
            }
            if (c == ',') {
                // 容忍尾随逗号 / 空元素
                i++;
                continue;
            }
            if (c != '"') {
                return List.of();
            }

            i++;
            StringBuilder sb = new StringBuilder();
            boolean closed = false;
            while (i < n) {
                char ch = json.charAt(i);
                if (ch == '"') {
                    i++;
                    closed = true;
                    break;
                }
                if (ch != '\\') {
                    sb.append(ch);
                    i++;
                    continue;
                }
                i++;
                if (i >= n) {
                    return List.of();
                }
                char esc = json.charAt(i);
                switch (esc) {
                    case '"', '\\', '/' -> sb.append(esc);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 >= n) {
                            return List.of();
                        }
                        sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    default -> {
                        return List.of();
                    }
                }
                i++;
            }
            if (!closed) {
                return List.of();
            }
            items.add(sb.toString());
        }

        // 闭合方括号之后不允许再出现非空白内容
        return skipWhitespace(json, i) == n ? items : List.of();
    }

    /** 从 {@code from} 起跳过空白字符，返回第一个非空白位置（可能等于串长）。 */
    private static int skipWhitespace(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}
