package me.kirara.sable.alert.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.alert.entity.AlertRule;
import me.kirara.sable.alert.mapper.AlertRuleMapper;
import me.kirara.sable.alert.service.AlertRuleService;
import org.springframework.stereotype.Service;

/** AlertRule 业务服务实现。 */
@Service
public class AlertRuleServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> implements AlertRuleService {
}
