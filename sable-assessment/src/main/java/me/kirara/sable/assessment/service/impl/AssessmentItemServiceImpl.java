package me.kirara.sable.assessment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.assessment.entity.AssessmentItem;
import me.kirara.sable.assessment.mapper.AssessmentItemMapper;
import me.kirara.sable.assessment.service.AssessmentItemService;
import org.springframework.stereotype.Service;

/** AssessmentItem 业务服务实现。 */
@Service
public class AssessmentItemServiceImpl extends ServiceImpl<AssessmentItemMapper, AssessmentItem> implements AssessmentItemService {
}
