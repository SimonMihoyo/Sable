package me.kirara.sable.classify.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import me.kirara.sable.classify.dto.RuleMatchResult;
import me.kirara.sable.classify.dto.RuleSaveRequest;
import me.kirara.sable.classify.dto.RuleTestRequest;
import me.kirara.sable.classify.engine.ClassifyEngine;
import me.kirara.sable.classify.entity.ClassifyRule;
import me.kirara.sable.classify.service.ClassifyRuleService;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 分类分级规则接口 — /classify/rules */
@RestController
@RequestMapping("/classify/rules")
public class RuleController {

    private final ClassifyRuleService ruleService;
    private final ClassifyEngine classifyEngine;

    public RuleController(ClassifyRuleService ruleService, ClassifyEngine classifyEngine) {
        this.ruleService = ruleService;
        this.classifyEngine = classifyEngine;
    }

    @GetMapping
    public R<PageResult<ClassifyRule>> list(PageQuery query,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String sensitivity,
                                            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<ClassifyRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(category != null, ClassifyRule::getCategory, category)
               .eq(sensitivity != null, ClassifyRule::getSensitivity, sensitivity)
               .eq(status != null, ClassifyRule::getStatus, status)
               .like(query.getKeyword() != null, ClassifyRule::getName, query.getKeyword())
               .orderByDesc(ClassifyRule::getPriority);
        Page<ClassifyRule> page = ruleService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/{id}")
    public R<ClassifyRule> detail(@PathVariable Long id) {
        return R.ok(ruleService.getById(id));
    }

    @PostMapping
    public R<ClassifyRule> create(@Valid @RequestBody RuleSaveRequest request) {
        ClassifyRule rule = new ClassifyRule();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setCategory(request.getCategory());
        rule.setSensitivity(request.getSensitivity());
        rule.setMatchType(request.getMatchType());
        rule.setPattern(request.getPattern());
        rule.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        rule.setIsBuiltin(false);
        rule.setStatus("ACTIVE");
        ruleService.save(rule);
        return R.ok(rule);
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RuleSaveRequest request) {
        ClassifyRule rule = new ClassifyRule();
        rule.setId(id);
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setCategory(request.getCategory());
        rule.setSensitivity(request.getSensitivity());
        rule.setMatchType(request.getMatchType());
        rule.setPattern(request.getPattern());
        rule.setPriority(request.getPriority());
        ruleService.updateById(rule);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        ClassifyRule rule = new ClassifyRule();
        rule.setId(id);
        rule.setStatus(status);
        ruleService.updateById(rule);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ruleService.removeById(id);
        return R.ok();
    }

    @PostMapping("/test")
    public R<List<RuleMatchResult>> test(@RequestBody RuleTestRequest request) {
        return R.ok(classifyEngine.match(request.getColumnName(), request.getColumnComment(), request.getSamples())
                .stream().map(r -> {
                    RuleMatchResult result = new RuleMatchResult();
                    result.setRuleId(r.getId());
                    result.setRuleName(r.getName());
                    result.setCategory(r.getCategory());
                    result.setSensitivity(r.getSensitivity());
                    return result;
                }).toList());
    }
}
