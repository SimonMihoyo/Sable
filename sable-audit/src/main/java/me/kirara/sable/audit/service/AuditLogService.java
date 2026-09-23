package me.kirara.sable.audit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.audit.entity.AuditLog;

/** AuditLog 业务服务。 */
public interface AuditLogService extends IService<AuditLog> {

    /**
     * 追加一条审计日志，自动计算 prev_hash 与 hash 形成哈希链。
     *
     * @param log 待写入日志（无需填写 prevHash / hash）
     * @return 写入后的日志（含计算得到的哈希）
     */
    AuditLog append(AuditLog log);

    /**
     * 校验指定范围内的哈希链完整性。
     *
     * @return 校验结果，true 表示链未被篡改
     */
    boolean verifyChain(long fromId, long toId);
}
