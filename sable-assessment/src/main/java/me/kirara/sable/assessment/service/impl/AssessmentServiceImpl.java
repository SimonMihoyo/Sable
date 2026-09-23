package me.kirara.sable.assessment.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.assessment.entity.Assessment;
import me.kirara.sable.assessment.mapper.AssessmentMapper;
import me.kirara.sable.assessment.service.AssessmentService;
import org.springframework.stereotype.Service;

/** Assessment 业务服务实现。 */
@Service
public class AssessmentServiceImpl extends ServiceImpl<AssessmentMapper, Assessment> implements AssessmentService {
}
