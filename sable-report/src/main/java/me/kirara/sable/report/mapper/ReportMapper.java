package me.kirara.sable.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.report.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/** Report 数据访问层。 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
