package me.kirara.sable.classify.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.classify.entity.ClassifyRule;
import me.kirara.sable.classify.mapper.ClassifyRuleMapper;
import me.kirara.sable.classify.service.ClassifyRuleService;
import org.springframework.stereotype.Service;

/** ClassifyRule 业务服务实现。 */
@Service
public class ClassifyRuleServiceImpl extends ServiceImpl<ClassifyRuleMapper, ClassifyRule> implements ClassifyRuleService {
}
