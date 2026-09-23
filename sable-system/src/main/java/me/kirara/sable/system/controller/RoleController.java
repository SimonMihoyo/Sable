package me.kirara.sable.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import me.kirara.sable.common.R;
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

    @PostMapping
    public R<Role> create(@Valid @RequestBody RoleSaveRequest request) {
        Role role = new Role();
        role.setName(request.getName());
        role.setDisplayName(request.getDisplayName());
        role.setDescription(request.getDescription());
        role.setIsSystem(false);
        role.setPermissions("[]");
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
        return R.ok(roleService.getById(id));
    }

    @PutMapping("/{id}/permissions")
    public R<Void> setPermissions(@PathVariable Long id, @RequestBody RolePermissionRequest request) {
        Role role = new Role();
        role.setId(id);
        role.setPermissions(String.join(",", request.getPermissions() == null ? List.of() : request.getPermissions()));
        roleService.updateById(role);
        return R.ok();
    }
}
