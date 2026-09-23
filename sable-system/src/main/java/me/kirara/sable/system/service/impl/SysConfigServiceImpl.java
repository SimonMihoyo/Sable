package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.entity.SysConfig;
import me.kirara.sable.system.mapper.SysConfigMapper;
import me.kirara.sable.system.service.SysConfigService;
import org.springframework.stereotype.Service;

/** SysConfig 业务服务实现。 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {
}
