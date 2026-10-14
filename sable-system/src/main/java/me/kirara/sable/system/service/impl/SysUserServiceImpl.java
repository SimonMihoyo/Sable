package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.entity.TenantUser;
import me.kirara.sable.system.entity.TenantUserRole;
import me.kirara.sable.system.mapper.SysUserMapper;
import me.kirara.sable.system.mapper.TenantUserMapper;
import me.kirara.sable.system.mapper.TenantUserRoleMapper;
import me.kirara.sable.system.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** SysUser 业务服务实现。 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 用户状态：正常。 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** tenant_user 状态：正常。 */
    private static final String MEMBERSHIP_ACTIVE = "ACTIVE";

    private final PasswordEncoder passwordEncoder;
    private final TenantUserMapper tenantUserMapper;
    private final TenantUserRoleMapper tenantUserRoleMapper;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder,
                              TenantUserMapper tenantUserMapper,
                              TenantUserRoleMapper tenantUserRoleMapper) {
        this.passwordEncoder = passwordEncoder;
        this.tenantUserMapper = tenantUserMapper;
        this.tenantUserRoleMapper = tenantUserRoleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(Long tenantId, UserCreateRequest request) {
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        // 明文只在这里过一手，落库前必须变成 BCrypt 哈希
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDisplayName(request.getDisplayName());
        user.setStatus(STATUS_ACTIVE);
        user.setMfaEnabled(false);
        user.setLoginFailCount(0);
        save(user);

        // 建立租户归属：没有这一行，用户登录后装配不到租户，等于建了个登不进去的账号
        TenantUser membership = new TenantUser();
        membership.setTenantId(tenantId);
        membership.setUserId(user.getId());
        membership.setDepartment(request.getDepartment());
        membership.setStatus(MEMBERSHIP_ACTIVE);
        tenantUserMapper.insert(membership);

        assignRoles(tenantId, user.getId(), request.getRoleIds());
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long tenantId, Long userId, List<Long> roleIds) {
        tenantUserRoleMapper.delete(Wrappers.<TenantUserRole>lambdaQuery()
                .eq(TenantUserRole::getTenantId, tenantId)
                .eq(TenantUserRole::getUserId, userId));

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        // 入参可能带重复项或 null，去重后再落库，否则会撞唯一约束
        for (Long roleId : roleIds.stream().filter(Objects::nonNull).distinct().toList()) {
            TenantUserRole grant = new TenantUserRole();
            grant.setTenantId(tenantId);
            grant.setUserId(userId);
            grant.setRoleId(roleId);
            tenantUserRoleMapper.insert(grant);
        }
    }
}
