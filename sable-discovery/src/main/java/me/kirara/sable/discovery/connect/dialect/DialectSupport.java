package me.kirara.sable.discovery.connect.dialect;

import me.kirara.sable.discovery.entity.DataSource;

import java.util.Properties;

/** 方言共用的小工具：连接属性与 URL 参数拼接。 */
final class DialectSupport {

    private DialectSupport() {
    }

    /** 用户名 + 口令，各库都一样。 */
    static Properties credentials(DataSource source, String password) {
        Properties properties = new Properties();
        properties.setProperty("user", source.getUsername());
        properties.setProperty("password", password);
        return properties;
    }

    /**
     * 把用户填的 {@code jdbcOptions} 拼到 URL 后面。
     *
     * <p>允许用户自己写前导 {@code ?} / {@code &}（很多文档示例这么写），统一剥掉再拼；
     * 空值返回空串，调用方直接拼接即可。</p>
     *
     * @param separator {@code "?"}（MySQL / PostgreSQL）或 {@code ";"}（SQL Server 风格）
     */
    static String appendOptions(String separator, String jdbcOptions) {
        if (jdbcOptions == null) {
            return "";
        }
        String options = jdbcOptions.trim();
        while (options.startsWith("?") || options.startsWith("&") || options.startsWith(";")) {
            options = options.substring(1);
        }
        return options.isEmpty() ? "" : separator + options;
    }
}
