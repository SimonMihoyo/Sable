package me.kirara.sable.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.report.dto.ReportGenerateRequest;
import me.kirara.sable.report.dto.ReportScheduleRequest;
import me.kirara.sable.report.entity.Report;
import me.kirara.sable.report.entity.ReportSchedule;
import me.kirara.sable.report.service.ReportScheduleService;
import me.kirara.sable.report.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 报告接口 — /reports */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportScheduleService reportScheduleService;

    public ReportController(ReportService reportService, ReportScheduleService reportScheduleService) {
        this.reportService = reportService;
        this.reportScheduleService = reportScheduleService;
    }

    @PostMapping("/assessment")
    public R<Report> generateAssessment(@RequestBody ReportGenerateRequest request) {
        return R.ok(reportService.generate("ASSESSMENT", request.getFormat(), request.getParams()));
    }

    @PostMapping("/asset")
    public R<Report> generateAsset(@RequestBody ReportGenerateRequest request) {
        return R.ok(reportService.generate("ASSET", request.getFormat(), request.getParams()));
    }

    @PostMapping("/audit")
    public R<Report> generateAudit(@RequestBody ReportGenerateRequest request) {
        return R.ok(reportService.generate("AUDIT", request.getFormat(), request.getParams()));
    }

    @GetMapping
    public R<PageResult<Report>> list(PageQuery query,
                                      @RequestParam(required = false) String type) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null, Report::getType, type)
               .like(query.getKeyword() != null, Report::getName, query.getKeyword())
               .orderByDesc(Report::getCreatedAt);
        Page<Report> page = reportService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/{reportId}/download")
    public R<String> download(@PathVariable Long reportId) {
        // TODO 返回对象存储预签名下载地址
        return R.ok();
    }

    @PostMapping("/schedules")
    public R<ReportSchedule> schedule(@RequestBody ReportScheduleRequest request) {
        ReportSchedule schedule = new ReportSchedule();
        schedule.setName(request.getName());
        schedule.setType(request.getType());
        schedule.setFormat(request.getFormat());
        schedule.setCronExpression(request.getCronExpression());
        schedule.setIsEnabled(request.getEnabled() == null || request.getEnabled());
        reportScheduleService.save(schedule);
        return R.ok(schedule);
    }
}
