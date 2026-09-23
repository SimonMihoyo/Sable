package me.kirara.sable.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.system.dto.StatusRequest;
import me.kirara.sable.system.dto.TenantCreateRequest;
import me.kirara.sable.system.dto.TenantUpdateRequest;
import me.kirara.sable.system.entity.Tenant;
import me.kirara.sable.system.service.TenantService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 租户管理接口 — /system/tenants */
@RestController
@RequestMapping("/system/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public R<Tenant> create(@Valid @RequestBody TenantCreateRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setCode(request.getCode());
        tenant.setPlanType(request.getPlanType() == null ? "BASIC" : request.getPlanType());
        tenant.setStatus("ACTIVE");
        tenant.setContactName(request.getContactName());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setContactPhone(request.getContactPhone());
        tenantService.save(tenant);
        return R.ok(tenant);
    }

    @GetMapping
    public R<PageResult<Tenant>> list(PageQuery query,
                                      @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Tenant::getStatus, status)
               .like(query.getKeyword() != null, Tenant::getName, query.getKeyword())
               .orderByDesc(Tenant::getCreatedAt);
        Page<Tenant> page = tenantService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Tenant> detail(@PathVariable Long id) {
        return R.ok(tenantService.getById(id));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody TenantUpdateRequest request) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName(request.getName());
        tenant.setPlanType(request.getPlanType());
        tenant.setContactName(request.getContactName());
        tenant.setContactEmail(request.getContactEmail());
        tenant.setContactPhone(request.getContactPhone());
        tenantService.updateById(tenant);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setStatus(request.getStatus());
        tenantService.updateById(tenant);
        return R.ok();
    }

    @GetMapping("/{id}/usage")
    public R<Object> usage(@PathVariable Long id) {
        // TODO 汇总数据源、用户、存储用量
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.removeById(id);
        return R.ok();
    }
}
