package me.kirara.sable.report.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.kirara.sable.report.entity.Report;

/** Report 业务服务。 */
public interface ReportService extends IService<Report> {

    /**
     * 提交报告生成任务（异步）。
     *
     * @param type 报告类型 ASSESSMENT / ASSET / AUDIT
     * @param format 输出格式 PDF / EXCEL
     * @param params 生成参数（JSON）
     * @return 处于生成中状态的报告记录
     */
    Report generate(String type, String format, String params);
}
