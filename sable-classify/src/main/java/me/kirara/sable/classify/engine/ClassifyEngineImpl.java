package me.kirara.sable.classify.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.kirara.sable.classify.entity.ClassifyRule;
import me.kirara.sable.classify.service.ClassifyRuleService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 分类分级引擎实现 —— 从库里取启用中的规则，逐条交给 {@link RuleMatcher} 判定。
 *
 * <p><b>规则来自数据库，不硬编码：</b>内置规则只是"建租户时写进 {@code classify_rule} 的初始数据"，
 * 运行期与租户自建规则一视同仁（都能停用、改优先级）。引擎只认 {@code status = ACTIVE}
 * 且未删除的规则，按 {@code priority} 从高到低返回命中结果。</p>
 *
 * <p>每次匹配都会读一次规则表：规则只有几十条、且带租户上下文（走租户 schema）。
 * 如果将来做"扫描完成后批量识别几万个字段"，这里应该换成一次加载、批量匹配，
 * 而不是每个字段查一次库 —— 那属于排期 ID 31（自动分级执行）的优化点。</p>
 */
@Component
public class ClassifyEngineImpl implements ClassifyEngine {

    private final ClassifyRuleService ruleService;

    private final RuleMatcher ruleMatcher;

    public ClassifyEngineImpl(ClassifyRuleService ruleService, RuleMatcher ruleMatcher) {
        this.ruleService = ruleService;
        this.ruleMatcher = ruleMatcher;
    }

    @Override
    public List<ClassifyRule> match(String columnName, String columnComment, List<String> samples) {
        if (!StringUtils.hasText(columnName) && !StringUtils.hasText(columnComment)
                && (samples == null || samples.isEmpty())) {
            return List.of();
        }
        List<ClassifyRule> rules = ruleService.list(new LambdaQueryWrapper<ClassifyRule>()
                .eq(ClassifyRule::getStatus, "ACTIVE")
                .isNull(ClassifyRule::getDeletedAt)
                .orderByDesc(ClassifyRule::getPriority)
                .orderByAsc(ClassifyRule::getId));
        return rules.stream()
                .filter(rule -> ruleMatcher.matches(rule, columnName, columnComment, samples))
                .toList();
    }
}
