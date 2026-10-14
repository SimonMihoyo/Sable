package me.kirara.sable.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.R;
import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.util.JsonArrays;
import me.kirara.sable.system.dto.RolePermissionRequest;
import me.kirara.sable.system.dto.RoleSaveRequest;
import me.kirara.sable.system.entity.Role;
import me.kirara.sable.system.service.RoleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 角色管理接口 — /system/roles */
@RestController
@RequestMapping("/system/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public R<List<Role>> list() {
        return R.ok(roleService.list(new LambdaQueryWrapper<Role>().orderByDesc(Role::getCreatedAt)));
    }

    /**
     * 创建角色。
     *
     * <p>{@code role.tenant_id} 是 {@code NOT NULL REFERENCES tenant(id)} —— 角色是**租户内的**
     * 资产，唯一约束 {@code uq_role_tenant_name} 就建在 {@code (tenant_id, name)} 上，
     * 不同租户可以有同名角色。所以归属必须显式写入：漏掉它不会"退化成公共角色"，
     * 而是直接撞 not-null 约束。</p>
     *
     * <p>租户取自访问令牌的 {@code tid}（见 {@code JwtAuthenticationFilter} 写入的
     * {@link TenantContext}），缺失时由 {@link TenantContext#requireTenantId()} 报
     * {@code 40104}，与 {@code TenantRequiredInterceptor} 对租户范围查询的兜底同一错误码。</p>
     */
    @PostMapping
    public R<Role> create(@Valid @RequestBody RoleSaveRequest request) {
        Role role = new Role();
        role.setTenantId(TenantContext.requireTenantId());
        role.setName(request.getName());
        role.setDisplayName(request.getDisplayName());
        role.setDescription(request.getDescription());
        role.setIsSystem(false);
        // permissions 是 JSONB 列，只接受合法 JSON，统一经 JsonArrays 构造
        role.setPermissions(JsonArrays.toJsonArray(List.of()));
        roleService.save(role);
        return R.ok(role);
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        Role role = new Role();
        role.setId(id);
        role.setName(request.getName());
        role.setDisplayName(request.getDisplayName());
        role.setDescription(request.getDescription());
        roleService.updateById(role);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        // TODO 系统预置角色不可删除
        roleService.removeById(id);
        return R.ok();
    }

    @GetMapping("/{id}/permissions")
    public R<Role> permissions(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null || role.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        return R.ok(role);
    }

    /**
     * 设置角色权限。
     *
     * <p>{@code role.permissions} 是 {@code JSONB} 列，只接受合法 JSON 文本。
     * 必须经 {@link JsonArrays} 序列化后写入 —— 直接把权限标识拼成
     * {@code String.join(",", ...)} 会得到 {@code data_source:read,audit_log:read}
     * 这种既非合法 JSON 也无从解析的字符串，写库时被数据库直接拒绝。</p>
     */
    @PutMapping("/{id}/permissions")
    public R<Void> setPermissions(@PathVariable Long id, @RequestBody RolePermissionRequest request) {
        Role existing = roleService.getById(id);
        if (existing == null || existing.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        Role role = new Role();
        role.setId(id);
        role.setPermissions(JsonArrays.toJsonArray(request.getPermissions()));
        roleService.updateById(role);
        return R.ok();
    }
}
