package me.kirara.sable.classify.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.kirara.sable.classify.entity.ClassifyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 单条规则的匹配逻辑 —— 纯计算，不碰数据库。
 *
 * <p>四种匹配方式：</p>
 * <ul>
 *   <li><b>REGEX</b>：{@code pattern} 是正则，对字段名 / 注释 / 采样值做 {@code find()}
 *       （子串命中即可，用户写 {@code ^...$} 时自然变成整串匹配）；</li>
 *   <li><b>KEYWORD</b>：{@code pattern} 是 JSON 数组（如 {@code ["手机号","phone"]}），
 *       任一关键词出现在字段名 / 注释 / 采样值里即命中，大小写不敏感；</li>
 *   <li><b>COMPOSITE</b>：{@code pattern} 是 JSON 对象
 *       {@code {"regex":"...","keywords":[...],"min_match":2}}，正则命中记 1 分、
 *       关键词命中记 1 分，达到 {@code min_match}（默认 2）才算命中 ——
 *       用来压住"光看名字像、实际不是"的误报；</li>
 *   <li><b>SAMPLE</b>：{@code pattern} 当正则用，**只**对采样值匹配；没有采样值时不命中。</li>
 * </ul>
 *
 * <p><b>坏规则不能让整次匹配失败：</b>正则写错、JSON 写错都只跳过这一条并记 WARN，
 * 否则一条脏数据会让所有字段都识别不出来。</p>
 */
@Component
public class RuleMatcher {

    private static final Logger log = LoggerFactory.getLogger(RuleMatcher.class);

    private static final int DEFAULT_MIN_MATCH = 2;

    private final ObjectMapper objectMapper;

    public RuleMatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 判断一条规则是否命中。
     *
     * @param rule          规则（matchType / pattern）
     * @param columnName    字段名，可空
     * @param columnComment 字段注释，可空
     * @param samples       采样值，可空
     */
    public boolean matches(ClassifyRule rule, String columnName, String columnComment, List<String> samples) {
        String name = lower(columnName);
        String comment = lower(columnComment);
        List<String> values = samples == null ? List.of()
                : samples.stream().filter(StringUtils::hasText).map(this::lower).toList();
        String matchType = rule.getMatchType() == null ? "" : rule.getMatchType();
        try {
            return switch (matchType) {
                case "REGEX" -> regexMatches(rule.getPattern(), name, comment, values, false);
                case "SAMPLE" -> regexMatches(rule.getPattern(), name, comment, values, true);
                case "KEYWORD" -> keywordMatches(rule.getPattern(), name, comment, values);
                case "COMPOSITE" -> compositeMatches(rule.getPattern(), name, comment, values);
                default -> false;
            };
        } catch (RuntimeException e) {
            // 单条规则坏掉不应影响整次识别；日志里带 ruleId，方便定位是哪条
            log.warn("规则匹配失败，已跳过 ruleId={} name={}: {}", rule.getId(), rule.getName(), e.getMessage());
            return false;
        }
    }

    private boolean regexMatches(String pattern, String name, String comment, List<String> samples,
                                 boolean samplesOnly) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        Pattern compiled = compile(pattern);
        if (!samplesOnly && (compiled.matcher(name).find() || compiled.matcher(comment).find())) {
            return true;
        }
        return samples.stream().anyMatch(sample -> compiled.matcher(sample).find());
    }

    private boolean keywordMatches(String pattern, String name, String comment, List<String> samples) {
        List<String> keywords = parseKeywords(pattern);
        return anyKeywordHit(keywords, name, comment, samples);
    }

    private boolean compositeMatches(String pattern, String name, String comment, List<String> samples) {
        JsonNode node = readJson(pattern, "COMPOSITE");
        int minMatch = node.path("min_match").asInt(DEFAULT_MIN_MATCH);
        int score = 0;
        if (regexMatches(node.path("regex").asText(null), name, comment, samples, false)) {
            score++;
        }
        if (anyKeywordHit(readKeywords(node.path("keywords")), name, comment, samples)) {
            score++;
        }
        return score >= minMatch;
    }

    /** keyword 规则：pattern 必须是 JSON 数组；解析失败抛异常，由调用方跳过该规则。 */
    private List<String> parseKeywords(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return List.of();
        }
        return readKeywords(readJson(pattern, "KEYWORD"));
    }

    private List<String> readKeywords(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("keywords 必须是 JSON 数组");
        }
        List<String> keywords = new ArrayList<>();
        node.forEach(item -> {
            String keyword = item.asText("");
            if (!keyword.isBlank()) {
                keywords.add(lower(keyword));
            }
        });
        return keywords;
    }

    private JsonNode readJson(String pattern, String matchType) {
        if (!StringUtils.hasText(pattern)) {
            throw new IllegalArgumentException(matchType + " 规则的 pattern 不能为空");
        }
        try {
            return objectMapper.readTree(pattern);
        } catch (Exception e) {
            throw new IllegalArgumentException(matchType + " 规则的 pattern 不是合法 JSON: " + e.getMessage());
        }
    }

    private boolean anyKeywordHit(List<String> keywords, String name, String comment, List<String> samples) {
        for (String keyword : keywords) {
            if (name.contains(keyword) || comment.contains(keyword)) {
                return true;
            }
            for (String sample : samples) {
                if (sample.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Pattern compile(String regex) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("正则不合法: " + e.getDescription());
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
