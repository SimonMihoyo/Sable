package me.kirara.sable.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import me.kirara.sable.common.BusinessException;
import me.kirara.sable.common.ErrorCode;
import me.kirara.sable.common.R;
import me.kirara.sable.common.TenantContext;
import me.kirara.sable.common.UserContext;
import me.kirara.sable.common.page.PageQuery;
import me.kirara.sable.common.page.PageResult;
import me.kirara.sable.system.auth.UserAuthority;
import me.kirara.sable.system.dto.StatusRequest;
import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.dto.UserResponse;
import me.kirara.sable.system.dto.UserUpdateRequest;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.service.SysUserService;
import me.kirara.sable.system.service.UserAuthorityService;
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
    private final UserAuthorityService userAuthorityService;

    public UserController(SysUserService sysUserService, UserAuthorityService userAuthorityService) {
        this.sysUserService = sysUserService;
        this.userAuthorityService = userAuthorityService;
    }

    /**
     * 创建用户。
     *
     * <p>密码的哈希在 {@link SysUserService#createUser} 内完成；响应经 {@link UserResponse}
     * 脱敏，不会回吐 {@code passwordHash}。租户取自访问令牌，新用户会连同
     * {@code department} 与 {@code roleIds} 一并落到该租户下。</p>
     */
    @PostMapping
    public R<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return R.ok(UserResponse.from(sysUserService.createUser(TenantContext.requireTenantId(), request)));
    }

    @GetMapping
    public R<PageResult<UserResponse>> list(PageQuery query,
                                            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, SysUser::getStatus, status)
               .and(query.getKeyword() != null, w -> w
                       .like(SysUser::getUsername, query.getKeyword())
                       .or().like(SysUser::getDisplayName, query.getKeyword()))
               .orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> page = sysUserService.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return R.ok(PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                UserResponse.fromAll(page.getRecords())));
    }

    /**
     * 获取当前登录用户信息。
     *
     * <p>用户身份由 {@code JwtAuthenticationFilter} 从 {@code Authorization} 头解析后写入
     * {@link UserContext}，本接口据此回查并走 {@link UserResponse} 脱敏投影返回。</p>
     *
     * <p><b>角色与权限按当前请求的租户装配</b>（{@code TenantContext} 里的 {@code tid}，
     * 由令牌携带）—— 不用 {@code UserAuthorityService#resolve} 那套"最早加入的租户"推定：
     * 同一个人在不同租户可以是不同角色，前端据此渲染的菜单必须与本次访问的数据范围一致。</p>
     *
     * <p>剩下的未装配项是 {@code department}（数据范围权限的前提，见
     * {@code tenant_user.department}，目前只存不读）。</p>
     */
    @GetMapping("/me")
    public R<UserResponse> me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 本接口不在白名单内，正常应由认证过滤器拦下；此处兜底，避免拿 null 去查库
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null || user.getDeletedAt() != null) {
            // 令牌本身有效，但它指向的用户已不存在（例如账号已被删除）——按认证失效处理
            throw new BusinessException(ErrorCode.AUTH_FAILED);
        }

        UserResponse response = UserResponse.from(user);
        UserAuthority authority = userAuthorityService.resolveInTenant(userId, TenantContext.getTenantId());
        response.setRoles(authority.getRoleNames());
        response.setPermissions(authority.getPermissions());
        return R.ok(response);
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

    /**
     * 设置用户在当前租户下的角色集合。
     *
     * <p>覆盖式语义：入参即该用户的最终角色集合，未列入的角色会被收回。</p>
     */
    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        sysUserService.assignRoles(TenantContext.requireTenantId(), id, roleIds);
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
