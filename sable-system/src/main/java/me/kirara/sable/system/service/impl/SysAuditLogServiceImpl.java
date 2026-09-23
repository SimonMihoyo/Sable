package me.kirara.sable.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.system.entity.SysAuditLog;
import me.kirara.sable.system.mapper.SysAuditLogMapper;
import me.kirara.sable.system.service.SysAuditLogService;
import org.springframework.stereotype.Service;

/** SysAuditLog 业务服务实现。 */
@Service
public class SysAuditLogServiceImpl extends ServiceImpl<SysAuditLogMapper, SysAuditLog> implements SysAuditLogService {
}
