package me.kirara.sable.alert.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.alert.entity.AlertEvent;

/** AlertEvent 业务服务。 */
public interface AlertEventService extends IService<AlertEvent> {

    /**
     * 由审计事件触发告警匹配，命中规则时生成告警事件并分发通知。
     *
     * @param ruleId 命中的规则 ID
     * @param triggerUserId 触发用户 ID
     * @param triggerDetail 触发详情（JSON）
     * @return 生成的告警事件
     */
    AlertEvent raise(Long ruleId, Long triggerUserId, String triggerDetail);
}
