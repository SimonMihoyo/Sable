package me.kirara.sable.discovery.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.kirara.sable.common.Permission;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.discovery.dto.ScanCreateRequest;
import me.kirara.sable.discovery.entity.ScanTask;
import me.kirara.sable.discovery.service.ScanTaskService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据资产扫描接口 — /discovery。
 *
 * <p>创建是异步的：接口返回 {@code PENDING}，真正的扫描在 {@code scanTaskExecutor}
 * 线程里跑（见 {@link ScanTaskService#submit}）。客户端用它拿到的 id 轮询
 * {@code GET /discovery/scans/{taskId}} 看状态与统计。</p>
 */
@RestController
@RequestMapping("/discovery")
public class ScanController {

    private final ScanTaskService scanTaskService;

    public ScanController(ScanTaskService scanTaskService) {
        this.scanTaskService = scanTaskService;
    }

    @PostMapping("/sources/{sourceId}/scans")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<ScanTask> create(@PathVariable Long sourceId, @RequestBody ScanCreateRequest request) {
        return R.ok(scanTaskService.submit(sourceId, request));
    }

    @GetMapping("/sources/{sourceId}/scans")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_READ)")
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
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_READ)")
    public R<ScanTask> detail(@PathVariable Long taskId) {
        return R.ok(scanTaskService.getById(taskId));
    }

    @PostMapping("/scans/{taskId}/cancel")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<Void> cancel(@PathVariable Long taskId) {
        scanTaskService.cancel(taskId);
        return R.ok();
    }
}
