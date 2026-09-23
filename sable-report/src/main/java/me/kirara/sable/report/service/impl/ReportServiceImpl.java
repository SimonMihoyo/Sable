package me.kirara.sable.report.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.report.entity.Report;
import me.kirara.sable.report.mapper.ReportMapper;
import me.kirara.sable.report.service.ReportService;
import org.springframework.stereotype.Service;

/** Report 业务服务实现。 */
@Service
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    @Override
    public Report generate(String type, String format, String params) {
        Report report = new Report();
        report.setType(type);
        report.setFormat(format);
        report.setParams(params);
        report.setStatus("PENDING");
        save(report);
        // TODO 提交异步渲染任务（模板 + PDF/Excel 导出）
        return report;
    }
}
