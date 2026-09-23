package me.kirara.sable.assessment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import me.kirara.sable.assessment.dto.AssessmentCreateRequest;
import me.kirara.sable.assessment.dto.AssessmentItemUpdateRequest;
import me.kirara.sable.assessment.dto.RectifyRequest;
import me.kirara.sable.assessment.dto.StandardItemDto;
import me.kirara.sable.assessment.engine.AssessmentEngine;
import me.kirara.sable.assessment.entity.Assessment;
import me.kirara.sable.assessment.entity.AssessmentItem;
import me.kirara.sable.assessment.service.AssessmentItemService;
import me.kirara.sable.assessment.service.AssessmentService;
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

import java.util.List;

/** 合规评估接口 — /assessment */
@RestController
@RequestMapping("/assessment")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final AssessmentItemService assessmentItemService;
    private final AssessmentEngine assessmentEngine;

    public AssessmentController(AssessmentService assessmentService,
                                AssessmentItemService assessmentItemService,
                                AssessmentEngine assessmentEngine) {
        this.assessmentService = assessmentService;
        this.assessmentItemService = assessmentItemService;
        this.assessmentEngine = assessmentEngine;
    }

    @PostMapping("/tasks")
    public R<Assessment> create(@Valid @RequestBody AssessmentCreateRequest request) {
        Assessment assessment = new Assessment();
        assessment.setName(request.getName());
        assessment.setStandardType(request.getStandardType());
        assessment.setStandardVersion(request.getStandardVersion());
        assessment.setDescription(request.getDescription());
        assessment.setAssessorId(request.getAssessorId());
        assessment.setStatus("DRAFT");
        assessmentService.save(assessment);
        // TODO 依据标准模板初始化检查项并写入 total_items
        return R.ok(assessment);
    }

    @GetMapping("/tasks")
    public R<PageResult<Assessment>> list(PageQuery query,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String standardType) {
        LambdaQueryWrapper<Assessment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Assessment::getStatus, status)
               .eq(standardType != null, Assessment::getStandardType, standardType)
               .like(query.getKeyword() != null, Assessment::getName, query.getKeyword())
               .orderByDesc(Assessment::getCreatedAt);
        Page<Assessment> page = assessmentService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/tasks/{id}")
    public R<Assessment> detail(@PathVariable Long id) {
        return R.ok(assessmentService.getById(id));
    }

    @GetMapping("/tasks/{taskId}/items")
    public R<List<AssessmentItem>> items(@PathVariable Long taskId) {
        return R.ok(assessmentItemService.list(
                new LambdaQueryWrapper<AssessmentItem>().eq(AssessmentItem::getAssessmentId, taskId)));
    }

    @PutMapping("/tasks/{taskId}/items/{itemId}")
    public R<Void> updateItem(@PathVariable Long taskId, @PathVariable Long itemId,
                              @RequestBody AssessmentItemUpdateRequest request) {
        AssessmentItem item = new AssessmentItem();
        item.setId(itemId);
        item.setStatus(request.getStatus());
        item.setEvidence(request.getEvidence());
        item.setRemark(request.getRemark());
        assessmentItemService.updateById(item);
        return R.ok();
    }

    @PostMapping("/tasks/{taskId}/auto-evaluate")
    public R<Void> autoEvaluate(@PathVariable Long taskId) {
        Assessment assessment = assessmentService.getById(taskId);
        List<AssessmentItem> items = assessmentItemService.list(
                new LambdaQueryWrapper<AssessmentItem>().eq(AssessmentItem::getAssessmentId, taskId));
        assessmentEngine.autoEvaluate(assessment, items);
        assessmentEngine.recalculate(assessment, items);
        assessmentService.updateById(assessment);
        return R.ok();
    }

    @PutMapping("/tasks/{taskId}/items/{itemId}/rectify")
    public R<Void> rectify(@PathVariable Long taskId, @PathVariable Long itemId,
                           @RequestBody RectifyRequest request) {
        AssessmentItem item = new AssessmentItem();
        item.setId(itemId);
        item.setHasGap(request.getHasGap());
        item.setGapDescription(request.getGapDescription());
        item.setSuggestion(request.getSuggestion());
        item.setAssigneeId(request.getAssigneeId());
        item.setDueDate(request.getDueDate());
        item.setRectifyStatus(request.getRectifyStatus());
        assessmentItemService.updateById(item);
        return R.ok();
    }

    @PostMapping("/tasks/{taskId}/complete")
    public R<Void> complete(@PathVariable Long taskId) {
        Assessment assessment = new Assessment();
        assessment.setId(taskId);
        assessment.setStatus("COMPLETED");
        assessmentService.updateById(assessment);
        return R.ok();
    }

    @GetMapping("/standards/{standardType}/items")
    public R<List<StandardItemDto>> standardItems(@PathVariable String standardType) {
        // TODO 加载内置标准检查项模板
        return R.ok(List.of());
    }
}
