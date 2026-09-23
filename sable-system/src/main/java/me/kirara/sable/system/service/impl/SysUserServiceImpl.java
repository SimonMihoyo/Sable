package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.entity.SysUser;
import me.kirara.sable.system.mapper.SysUserMapper;
import me.kirara.sable.system.service.SysUserService;
import org.springframework.stereotype.Service;

/** SysUser 业务服务实现。 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}
