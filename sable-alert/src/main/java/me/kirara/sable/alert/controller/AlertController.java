package me.kirara.sable.alert.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import me.kirara.sable.alert.dto.AlertHandleRequest;
import me.kirara.sable.alert.dto.AlertRuleSaveRequest;
import me.kirara.sable.alert.dto.BatchHandleRequest;
import me.kirara.sable.alert.entity.AlertEvent;
import me.kirara.sable.alert.entity.AlertRule;
import me.kirara.sable.alert.service.AlertEventService;
import me.kirara.sable.alert.service.AlertRuleService;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 告警管理接口 — /alert */
@RestController
@RequestMapping("/alert")
public class AlertController {

    private final AlertRuleService alertRuleService;
    private final AlertEventService alertEventService;

    public AlertController(AlertRuleService alertRuleService, AlertEventService alertEventService) {
        this.alertRuleService = alertRuleService;
        this.alertEventService = alertEventService;
    }

    @GetMapping("/rules")
    public R<PageResult<AlertRule>> rules(PageQuery query,
                                          @RequestParam(required = false) String severity,
                                          @RequestParam(required = false) Boolean enabled) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(severity != null, AlertRule::getSeverity, severity)
               .eq(enabled != null, AlertRule::getIsEnabled, enabled)
               .like(query.getKeyword() != null, AlertRule::getName, query.getKeyword())
               .orderByDesc(AlertRule::getCreatedAt);
        Page<AlertRule> page = alertRuleService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @PostMapping("/rules")
    public R<AlertRule> createRule(@Valid @RequestBody AlertRuleSaveRequest request) {
        AlertRule rule = new AlertRule();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setConfig(request.getConfig());
        rule.setSeverity(request.getSeverity() == null ? "WARNING" : request.getSeverity());
        rule.setNotifyChannels("[\"SYSTEM\"]");
        rule.setIsEnabled(true);
        alertRuleService.save(rule);
        return R.ok(rule);
    }

    @PutMapping("/rules/{id}")
    public R<Void> updateRule(@PathVariable Long id, @Valid @RequestBody AlertRuleSaveRequest request) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setConfig(request.getConfig());
        rule.setSeverity(request.getSeverity());
        alertRuleService.updateById(rule);
        return R.ok();
    }

    @PutMapping("/rules/{id}/toggle")
    public R<Void> toggleRule(@PathVariable Long id, @RequestParam Boolean enabled) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setIsEnabled(enabled);
        alertRuleService.updateById(rule);
        return R.ok();
    }

    @GetMapping("/events")
    public R<PageResult<AlertEvent>> events(PageQuery query,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String severity) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, AlertEvent::getStatus, status)
               .eq(severity != null, AlertEvent::getSeverity, severity)
               .orderByDesc(AlertEvent::getCreatedAt);
        Page<AlertEvent> page = alertEventService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @PutMapping("/events/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @RequestBody AlertHandleRequest request) {
        AlertEvent event = new AlertEvent();
        event.setId(id);
        event.setStatus(request.getStatus());
        event.setHandleRemark(request.getHandleRemark());
        event.setHandledAt(LocalDateTime.now());
        alertEventService.updateById(event);
        return R.ok();
    }

    @PutMapping("/events/batch-handle")
    public R<Void> batchHandle(@RequestBody BatchHandleRequest request) {
        // TODO 批量更新事件状态
        return R.ok();
    }
}
