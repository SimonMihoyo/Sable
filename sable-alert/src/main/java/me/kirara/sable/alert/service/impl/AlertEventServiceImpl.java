package me.kirara.sable.alert.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.alert.entity.AlertEvent;
import me.kirara.sable.alert.mapper.AlertEventMapper;
import me.kirara.sable.alert.service.AlertEventService;
import org.springframework.stereotype.Service;

/** AlertEvent 业务服务实现。 */
@Service
public class AlertEventServiceImpl extends ServiceImpl<AlertEventMapper, AlertEvent> implements AlertEventService {

    @Override
    public AlertEvent raise(Long ruleId, Long triggerUserId, String triggerDetail) {
        AlertEvent event = new AlertEvent();
        event.setRuleId(ruleId);
        event.setTriggerUserId(triggerUserId);
        event.setTriggerDetail(triggerDetail);
        event.setStatus("PENDING");
        save(event);
        // TODO 依据规则 notify_channels 分发系统 / 邮件 / 微信 / 钉钉通知
        return event;
    }
}
