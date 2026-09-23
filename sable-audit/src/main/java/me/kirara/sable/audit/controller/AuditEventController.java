package me.kirara.sable.audit.controller;

import jakarta.validation.Valid;
import me.kirara.sable.audit.dto.AuditEventRequest;
import me.kirara.sable.audit.entity.AuditLog;
import me.kirara.sable.audit.service.AuditLogService;
import me.kirara.sable.common.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SDK 审计事件上报接口 — /audit/events */
@RestController
@RequestMapping("/audit/events")
public class AuditEventController {

    private final AuditLogService auditLogService;

    public AuditEventController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public R<Void> report(@Valid @RequestBody AuditEventRequest request) {
        AuditLog log = new AuditLog();
        log.setUserId(request.getUserId());
        log.setUserName(request.getUserName());
        log.setOperation(request.getOperation());
        log.setTargetDesc(request.getTargetDesc());
        log.setTargetLevel(request.getTargetLevel());
        log.setSourceIp(request.getSourceIp());
        log.setResult(request.getResult() == null ? "SUCCESS" : request.getResult());
        log.setDurationMs(request.getDurationMs());
        log.setOperationContext(request.getContext());
        auditLogService.append(log);
        return R.ok();
    }
}
