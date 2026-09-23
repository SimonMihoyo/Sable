package me.kirara.sable.discovery.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.discovery.dto.BatchSensitivityRequest;
import me.kirara.sable.discovery.dto.ColumnSensitivityRequest;
import me.kirara.sable.discovery.entity.DbColumn;
import me.kirara.sable.discovery.service.DbColumnService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 数据资产查询接口 — /discovery */
@RestController
@RequestMapping("/discovery")
public class AssetController {

    private final DbColumnService dbColumnService;

    public AssetController(DbColumnService dbColumnService) {
        this.dbColumnService = dbColumnService;
    }

    @GetMapping("/sources/{sourceId}/assets/tree")
    public R<List<Object>> tree(@PathVariable Long sourceId) {
        // TODO 组装 source -> schema -> table -> column 资产树
        return R.ok(List.of());
    }

    @GetMapping("/sources/{sourceId}/columns")
    public R<PageResult<DbColumn>> columns(@PathVariable Long sourceId, PageQuery query) {
        // TODO 按 source 关联 table 过滤
        LambdaQueryWrapper<DbColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(query.getKeyword() != null, DbColumn::getColumnName, query.getKeyword());
        Page<DbColumn> page = dbColumnService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @PutMapping("/columns/{columnId}/sensitivity")
    public R<Void> updateSensitivity(@PathVariable Long columnId,
                                     @RequestBody ColumnSensitivityRequest request) {
        DbColumn column = new DbColumn();
        column.setId(columnId);
        column.setSensitivity(request.getSensitivity());
        column.setCategory(request.getCategory());
        column.setMaskingRule(request.getMaskingRule());
        column.setEncryptionReq(request.getEncryptionReq());
        column.setManualOverride(true);
        dbColumnService.updateById(column);
        return R.ok();
    }

    @PutMapping("/columns/batch-sensitivity")
    public R<Void> batchSensitivity(@RequestBody BatchSensitivityRequest request) {
        // TODO 批量更新分级
        return R.ok();
    }

    @GetMapping("/sources/{sourceId}/export")
    public R<String> export(@PathVariable Long sourceId) {
        // TODO 生成 Excel 并返回下载地址
        return R.ok();
    }
}
