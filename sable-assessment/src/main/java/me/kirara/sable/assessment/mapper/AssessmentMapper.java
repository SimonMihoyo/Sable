package me.kirara.sable.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.assessment.entity.Assessment;
import org.apache.ibatis.annotations.Mapper;

/** Assessment 数据访问层。 */
@Mapper
public interface AssessmentMapper extends BaseMapper<Assessment> {
}
