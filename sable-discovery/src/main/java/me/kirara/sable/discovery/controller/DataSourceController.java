package me.kirara.sable.discovery.controller;

import jakarta.validation.Valid;
import me.kirara.sable.common.Permission;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.discovery.dto.ConnectionTestResult;
import me.kirara.sable.discovery.dto.DataSourceQuery;
import me.kirara.sable.discovery.dto.DataSourceResult;
import me.kirara.sable.discovery.dto.DataSourceSaveRequest;
import me.kirara.sable.discovery.dto.ScheduleRequest;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.discovery.service.DataSourceService;
import me.kirara.sable.discovery.service.ScanScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源管理接口 — /discovery/sources。
 *
 * <p>控制器只做参数接收与投影转换，业务规则（查重、加密、软删、级联）都在
 * {@link DataSourceService}。响应一律是 {@link DataSourceResult} 而不是实体 ——
 * 实体上的 {@code password_enc} 不该出网。</p>
 *
 * <p>权限码见 {@link Permission#DATA_SOURCE_READ} / {@link Permission#DATA_SOURCE_WRITE}；
 * 读接口给读权限，写接口（含连通性测试）给写权限。</p>
 */
@RestController
@RequestMapping("/discovery/sources")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    private final ScanScheduleService scanScheduleService;

    public DataSourceController(DataSourceService dataSourceService,
                                ScanScheduleService scanScheduleService) {
        this.dataSourceService = dataSourceService;
        this.scanScheduleService = scanScheduleService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<DataSourceResult> create(@Valid @RequestBody DataSourceSaveRequest request) {
        return R.ok(DataSourceResult.of(dataSourceService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_READ)")
    public R<PageResult<DataSourceResult>> list(DataSourceQuery query) {
        PageResult<DataSource> page = dataSourceService.page(query);
        return R.ok(PageResult.of(page.getPage(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(DataSourceResult::of).toList()));
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_READ)")
    public R<List<String>> groups() {
        return R.ok(dataSourceService.groups());
    }

    /**
     * 分组排序 —— 请求体是分组名数组，按期望的显示顺序排列。
     *
     * <p>顺序落在 {@code data_source.group_sort} 上（分组是派生概念，没有独立表），
     * 见 {@link DataSourceService#reorderGroups(List)}。未出现在数组里的分组保持原顺序，
     * 不认识的组名忽略。</p>
     */
    @PutMapping("/groups/reorder")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<Void> reorderGroups(@RequestBody List<String> groups) {
        dataSourceService.reorderGroups(groups);
        return R.ok();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_READ)")
    public R<DataSourceResult> detail(@PathVariable Long id) {
        return R.ok(DataSourceResult.of(dataSourceService.requireById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DataSourceSaveRequest request) {
        dataSourceService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<Void> delete(@PathVariable Long id) {
        dataSourceService.deleteCascade(id);
        return R.ok();
    }

    /**
     * 配置定时扫描。
     *
     * <p>属于排期 ID 20：cron 存在数据源上（每数据源一个调度），由启动模块的
     * {@code ScanScheduleJob} 每 30 秒扫一次到期的数据源并提交扫描任务。
     * cron 支持 5 段（自动补秒位）与 6 段，统一存 6 段。</p>
     */
    @PutMapping("/{id}/schedule")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<Void> schedule(@PathVariable Long id, @RequestBody ScheduleRequest request) {
        scanScheduleService.updateSchedule(id, request);
        return R.ok();
    }

    /**
     * 连通性测试。
     *
     * <p>挂写权限：这个动作会用库里存的口令去连目标库，属于特权操作。
     * 连接失败不是 HTTP 错误 —— 返回 {@code code=0} + {@code success=false} + 原因，
     * 由客户端展示；数据源不存在才返回 40201。</p>
     */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority(T(me.kirara.sable.common.Permission).DATA_SOURCE_WRITE)")
    public R<ConnectionTestResult> test(@PathVariable Long id) {
        return R.ok(dataSourceService.testConnection(id));
    }
}
