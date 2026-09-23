package me.kirara.sable.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import me.kirara.sable.common.R;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.system.dto.StatusRequest;
import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.dto.UserUpdateRequest;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.service.SysUserService;
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

/** 用户管理接口 — /system/users */
@RestController
@RequestMapping("/system/users")
public class UserController {

    private final SysUserService sysUserService;

    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @PostMapping
    public R<SysUser> create(@Valid @RequestBody UserCreateRequest request) {
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        // TODO 使用 BCrypt 加密
        user.setPasswordHash(request.getPassword());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDisplayName(request.getDisplayName());
        user.setStatus("ACTIVE");
        user.setMfaEnabled(false);
        user.setLoginFailCount(0);
        sysUserService.save(user);
        return R.ok(user);
    }

    @GetMapping
    public R<PageResult<SysUser>> list(PageQuery query,
                                       @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, SysUser::getStatus, status)
               .and(query.getKeyword() != null, w -> w
                       .like(SysUser::getUsername, query.getKeyword())
                       .or().like(SysUser::getDisplayName, query.getKeyword()))
               .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> page = sysUserService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords()));
    }

    @GetMapping("/me")
    public R<SysUser> me() {
        // TODO 从安全上下文中获取当前用户
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDisplayName(request.getDisplayName());
        sysUserService.updateById(user);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        // 软删除
        sysUserService.removeById(id);
        return R.ok();
    }

    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        // TODO 维护 tenant_user.role_id
        return R.ok();
    }

    @PostMapping("/{id}/password-reset")
    public R<Void> resetPassword(@PathVariable Long id) {
        // TODO 生成临时密码并通知用户
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(request.getStatus());
        sysUserService.updateById(user);
        return R.ok();
    }
}
