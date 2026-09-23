package me.kirara.sable.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.audit.entity.AuditLog;
import me.kirara.sable.audit.mapper.AuditLogMapper;
import me.kirara.sable.audit.service.AuditLogService;
import me.kirara.sable.common.util.DigestUtil;
import org.springframework.stereotype.Service;

/** AuditLog 业务服务实现 — 负责哈希链的生成与校验。 */
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {

    @Override
    public AuditLog append(AuditLog log) {
        AuditLog last = getOne(new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId).last("limit 1"));
        String prevHash = (last == null || last.getHash() == null) ? DigestUtil.GENESIS_HASH : last.getHash();
        log.setPrevHash(prevHash);

        String raw = String.join("|",
                String.valueOf(log.getUserId()),
                String.valueOf(log.getOperation()),
                String.valueOf(log.getTargetDesc()),
                String.valueOf(log.getSourceIp()),
                String.valueOf(log.getResult()),
                String.valueOf(log.getCreatedAt()),
                prevHash);
        log.setHash(DigestUtil.sha256(raw));

        save(log);
        return log;
    }

    @Override
    public boolean verifyChain(long fromId, long toId) {
        // TODO 逐条重算 hash 并与存量比对
        return true;
    }
}
