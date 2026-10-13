package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.dto.UserCreateRequest;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.mapper.SysUserMapper;
import me.kirara.sable.system.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** SysUser 业务服务实现。 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 用户状态：正常。 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SysUser createUser(UserCreateRequest request) {
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
        return user;
    }
}
