package me.kirara.sable.discovery.connect.dialect;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 方言注册表 —— 按 {@code data_source.type} 找实现。
 *
 * <p>Spring 会把容器里所有 {@link JdbcDialect} 注入进来，新增一个库只要加一个
 * {@code @Component} 实现，这里不用改。</p>
 */
@Component
public class JdbcDialectRegistry {

    private final Map<String, JdbcDialect> byType;

    public JdbcDialectRegistry(List<JdbcDialect> dialects) {
        Map<String, JdbcDialect> map = new LinkedHashMap<>();
        for (JdbcDialect dialect : dialects) {
            map.put(dialect.type().toUpperCase(Locale.ROOT), dialect);
        }
        this.byType = Map.copyOf(map);
    }

    /** @return 对应方言；类型不支持时返回 {@code null} */
    public JdbcDialect resolve(String type) {
        return type == null ? null : byType.get(type.toUpperCase(Locale.ROOT));
    }

    public boolean supports(String type) {
        return resolve(type) != null;
    }

    /** 已支持的类型，用于错误提示（如 "POSTGRESQL / MYSQL"）。 */
    public String supportedTypes() {
        return byType.keySet().stream().sorted().collect(Collectors.joining(" / "));
    }
}
