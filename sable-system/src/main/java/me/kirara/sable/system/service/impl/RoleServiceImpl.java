package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.entity.Role;
import me.kirara.sable.system.mapper.RoleMapper;
import me.kirara.sable.system.service.RoleService;
import org.springframework.stereotype.Service;

/** Role 业务服务实现。 */
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {
}
