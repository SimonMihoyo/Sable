package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.entity.Tenant;
import me.kirara.sable.system.mapper.TenantMapper;
import me.kirara.sable.system.service.TenantService;
import org.springframework.stereotype.Service;

/** Tenant 业务服务实现。 */
@Service
public class TenantServiceImpl extends ServiceImpl<TenantMapper, Tenant> implements TenantService {
}
