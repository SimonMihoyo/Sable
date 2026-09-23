package me.kirara.sable.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.kirara.sable.audit.dto.AuditLogQuery;
import me.kirara.sable.audit.dto.AuditStatistics;
import me.kirara.sable.audit.dto.AuditVerifyRequest;
import me.kirara.sable.audit.dto.AuditVerifyResult;
import me.kirara.sable.audit.entity.AuditLog;
import me.kirara.sable.audit.service.AuditLogService;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审计日志接口 — /audit */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/logs")
    public R<PageResult<AuditLog>> list(AuditLogQuery query) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, AuditLog::getUserId, query.getUserId())
               .eq(query.getOperation() != null, AuditLog::getOperation, query.getOperation())
               .eq(query.getTargetLevel() != null, AuditLog::getTargetLevel, query.getTargetLevel())
               .eq(query.getResult() != null, AuditLog::getResult, query.getResult())
               .like(query.getUserName() != null, AuditLog::getUserName, query.getUserName())
               .ge(query.getStartTime() != null, AuditLog::getCreatedAt, query.getStartTime())
               .le(query.getEndTime() != null, AuditLog::getCreatedAt, query.getEndTime())
               .orderByDesc(AuditLog::getCreatedAt);
        Page<AuditLog> page = auditLogService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/logs/{id}")
    public R<AuditLog> detail(@PathVariable Long id) {
        return R.ok(auditLogService.getById(id));
    }

    @GetMapping("/statistics")
    public R<AuditStatistics> statistics() {
        // TODO 按操作类型 / 级别聚合统计
        return R.ok(new AuditStatistics());
    }

    @GetMapping("/logs/export")
    public R<String> export(AuditLogQuery query) {
        // TODO 异步导出并返回下载地址
        return R.ok();
    }

    @PostMapping("/logs/verify")
    public R<AuditVerifyResult> verify(@RequestBody AuditVerifyRequest request) {
        AuditVerifyResult result = new AuditVerifyResult();
        result.setValid(auditLogService.verifyChain(request.getFromId(), request.getToId()));
        return R.ok(result);
    }
}
