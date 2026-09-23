package me.kirara.sable.discovery.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.discovery.dto.ConnectionTestResult;
import me.kirara.sable.discovery.dto.DataSourceSaveRequest;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.discovery.service.DataSourceService;
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

/** 数据源管理接口 — /discovery/sources */
@RestController
@RequestMapping("/discovery/sources")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @PostMapping
    public R<DataSource> create(@Valid @RequestBody DataSourceSaveRequest request) {
        DataSource source = new DataSource();
        source.setName(request.getName());
        source.setType(request.getType());
        source.setHost(request.getHost());
        source.setPort(request.getPort());
        source.setDatabaseName(request.getDatabaseName());
        source.setUsername(request.getUsername());
        // TODO 加密密码 -> password_enc
        source.setJdbcOptions(request.getJdbcOptions());
        source.setGroupName(request.getGroupName());
        source.setDescription(request.getDescription());
        source.setStatus("INACTIVE");
        dataSourceService.save(source);
        return R.ok(source);
    }

    @GetMapping
    public R<PageResult<DataSource>> list(PageQuery query,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String groupName) {
        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null, DataSource::getType, type)
               .eq(status != null, DataSource::getStatus, status)
               .eq(groupName != null, DataSource::getGroupName, groupName)
               .like(query.getKeyword() != null, DataSource::getName, query.getKeyword())
               .orderByDesc(DataSource::getCreatedAt);
        Page<DataSource> page = dataSourceService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/groups")
    public R<List<String>> groups() {
        // TODO 查询去重的数据源分组
        return R.ok(List.of());
    }

    @PutMapping("/groups/reorder")
    public R<Void> reorderGroups(@RequestBody List<String> groups) {
        // TODO 调整分组排序
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<DataSource> detail(@PathVariable Long id) {
        return R.ok(dataSourceService.getById(id));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DataSourceSaveRequest request) {
        DataSource source = new DataSource();
        source.setId(id);
        source.setName(request.getName());
        source.setType(request.getType());
        source.setHost(request.getHost());
        source.setPort(request.getPort());
        source.setDatabaseName(request.getDatabaseName());
        source.setUsername(request.getUsername());
        source.setJdbcOptions(request.getJdbcOptions());
        source.setGroupName(request.getGroupName());
        source.setDescription(request.getDescription());
        dataSourceService.updateById(source);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dataSourceService.removeById(id);
        return R.ok();
    }

    @PostMapping("/{id}/test")
    public R<ConnectionTestResult> test(@PathVariable Long id) {
        // TODO 建立 JDBC 连接并返回耗时
        return R.ok(new ConnectionTestResult(false, "连接测试尚未实现", 0L));
    }
}
