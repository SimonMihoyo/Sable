package me.kirara.sable.classify;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.kirara.sable.classify.engine.RuleMatcher;
import me.kirara.sable.classify.entity.ClassifyRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RuleMatcher} 的匹配语义 —— 纯逻辑，不连库。
 *
 * <p>覆盖四种 matchType 的核心行为，以及"坏规则只跳过自己"这条兜底：
 * 一条正则写错的规则不能把整次识别拖垮。</p>
 */
class RuleMatcherTest {

    private final RuleMatcher matcher = new RuleMatcher(new ObjectMapper());

    private static ClassifyRule rule(String matchType, String pattern) {
        ClassifyRule rule = new ClassifyRule();
        rule.setId(1L);
        rule.setName("测试规则");
        rule.setMatchType(matchType);
        rule.setPattern(pattern);
        return rule;
    }

    @Test
    @DisplayName("REGEX：字段名、注释、采样值任一命中即可")
    void regexMatchesAnyField() {
        ClassifyRule rule = rule("REGEX", "手机号|phone|mobile");

        assertTrue(matcher.matches(rule, "user_phone", null, null));
        assertTrue(matcher.matches(rule, "contact", "用户手机号", null));
        assertFalse(matcher.matches(rule, "created_at", "创建时间", null));
        // 值匹配用的是号码正则，不是字段名关键词
        assertTrue(matcher.matches(rule("REGEX", "1[3-9]\\d{9}"), "value", null, List.of("13800138000")));
    }

    @Test
    @DisplayName("REGEX：带锚点的号码规则能命中采样值")
    void regexMatchesAnchoredValue() {
        ClassifyRule rule = rule("REGEX", "^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$");

        assertTrue(matcher.matches(rule, "cert_no", null, List.of("110101199001011234")));
        assertFalse(matcher.matches(rule, "cert_no", null, List.of("123456")));
    }

    @Test
    @DisplayName("KEYWORD：JSON 数组，大小写不敏感，中文可用")
    void keywordMatchesCaseInsensitively() {
        ClassifyRule rule = rule("KEYWORD", "[\"密码\",\"password\",\"pwd\"]");

        assertTrue(matcher.matches(rule, "USER_PASSWORD", null, null));
        assertTrue(matcher.matches(rule, "pwd", null, null));
        assertTrue(matcher.matches(rule, "field", "登录密码", null));
        assertFalse(matcher.matches(rule, "nickname", "昵称", null));
    }

    @Test
    @DisplayName("COMPOSITE(min_match=2)：名字像且值像才算命中")
    void compositeRequiresBothSignals() {
        ClassifyRule rule = rule("COMPOSITE",
                "{\"keywords\":[\"银行卡\",\"card_no\"],\"regex\":\"(?<!\\\\d)\\\\d{16,19}(?!\\\\d)\",\"min_match\":2}");

        // 只有字段名像：1 分，不够 2 分
        assertFalse(matcher.matches(rule, "card_no", null, null));
        // 名字像 + 值像：2 分
        assertTrue(matcher.matches(rule, "card_no", null, List.of("6222021234567890")));
        // 只有值像：1 分
        assertFalse(matcher.matches(rule, "remark", null, List.of("6222021234567890")));
    }

    @Test
    @DisplayName("COMPOSITE(min_match=1)：名字像或值像都算命中")
    void compositeWithSingleSignal() {
        ClassifyRule rule = rule("COMPOSITE",
                "{\"keywords\":[\"身份证\",\"id_card\"],\"regex\":\"^[1-9]\\\\d{5}\",\"min_match\":1}");

        assertTrue(matcher.matches(rule, "id_card", null, null));
        assertTrue(matcher.matches(rule, "value", null, List.of("110101199001011234")));
        assertFalse(matcher.matches(rule, "remark", null, null));
    }

    @Test
    @DisplayName("SAMPLE：只对采样值匹配，没有采样值就不命中")
    void sampleTypeOnlyUsesSamples() {
        ClassifyRule rule = rule("SAMPLE", "^1[3-9]\\d{9}$");

        assertFalse(matcher.matches(rule, "phone", null, null));
        assertTrue(matcher.matches(rule, "value", null, List.of("13800138000")));
        assertFalse(matcher.matches(rule, "phone", null, List.of("not-a-phone")));
    }

    @Test
    @DisplayName("坏规则只跳过自己：非法正则、非法 JSON、空 pattern 都返回 false 而不抛异常")
    void brokenRulesAreSkipped() {
        assertFalse(matcher.matches(rule("REGEX", "([unclosed"), "phone", null, null));
        assertFalse(matcher.matches(rule("KEYWORD", "not-json"), "phone", null, null));
        assertFalse(matcher.matches(rule("COMPOSITE", "not-json"), "phone", null, null));
        assertFalse(matcher.matches(rule("REGEX", "   "), "phone", null, null));
        assertFalse(matcher.matches(rule("UNKNOWN", "whatever"), "phone", null, null));
    }
}
