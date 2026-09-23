package me.kirara.sable.report.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.report.entity.ReportSchedule;
import me.kirara.sable.report.mapper.ReportScheduleMapper;
import me.kirara.sable.report.service.ReportScheduleService;
import org.springframework.stereotype.Service;

/** ReportSchedule 业务服务实现。 */
@Service
public class ReportScheduleServiceImpl extends ServiceImpl<ReportScheduleMapper, ReportSchedule> implements ReportScheduleService {
}
