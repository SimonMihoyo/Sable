package me.kirara.sable.discovery.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.discovery.dto.ScanCreateRequest;
import me.kirara.sable.discovery.dto.ScheduleRequest;
import me.kirara.sable.discovery.entity.ScanTask;
import me.kirara.sable.discovery.service.ScanTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 数据资产扫描接口 — /discovery */
@RestController
@RequestMapping("/discovery")
public class ScanController {

    private final ScanTaskService scanTaskService;

    public ScanController(ScanTaskService scanTaskService) {
        this.scanTaskService = scanTaskService;
    }

    @PostMapping("/sources/{sourceId}/scans")
    public R<ScanTask> create(@PathVariable Long sourceId, @RequestBody ScanCreateRequest request) {
        ScanTask task = new ScanTask();
        task.setSourceId(sourceId);
        task.setName(request.getName());
        task.setScanType(request.getScanType() == null ? "FULL" : request.getScanType());
        task.setTriggerType(request.getTriggerType() == null ? "MANUAL" : request.getTriggerType());
        task.setCronExpression(request.getCronExpression());
        task.setStatus("PENDING");
        scanTaskService.save(task);
        // TODO 提交异步扫描任务
        return R.ok(task);
    }

    @GetMapping("/sources/{sourceId}/scans")
    public R<PageResult<ScanTask>> list(@PathVariable Long sourceId, PageQuery query,
                                        @RequestParam(required = false) String status) {
        LambdaQueryWrapper<ScanTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScanTask::getSourceId, sourceId)
               .eq(status != null, ScanTask::getStatus, status)
               .orderByDesc(ScanTask::getCreatedAt);
        Page<ScanTask> page = scanTaskService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/scans/{taskId}")
    public R<ScanTask> detail(@PathVariable Long taskId) {
        return R.ok(scanTaskService.getById(taskId));
    }

    @PutMapping("/sources/{sourceId}/schedule")
    public R<Void> schedule(@PathVariable Long sourceId, @RequestBody ScheduleRequest request) {
        // TODO 注册 / 更新定时扫描任务
        return R.ok();
    }

    @PostMapping("/scans/{taskId}/cancel")
    public R<Void> cancel(@PathVariable Long taskId) {
        ScanTask task = new ScanTask();
        task.setId(taskId);
        task.setStatus("CANCELLED");
        scanTaskService.updateById(task);
        return R.ok();
    }
}
