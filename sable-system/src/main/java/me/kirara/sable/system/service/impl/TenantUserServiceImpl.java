package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.entity.TenantUser;
import me.kirara.sable.system.mapper.TenantUserMapper;
import me.kirara.sable.system.service.TenantUserService;
import org.springframework.stereotype.Service;

/** TenantUser 业务服务实现。 */
@Service
public class TenantUserServiceImpl extends ServiceImpl<TenantUserMapper, TenantUser> implements TenantUserService {
}
