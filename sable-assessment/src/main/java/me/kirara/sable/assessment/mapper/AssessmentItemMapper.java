package me.kirara.sable.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.assessment.entity.AssessmentItem;
import org.apache.ibatis.annotations.Mapper;

/** AssessmentItem 数据访问层。 */
@Mapper
public interface AssessmentItemMapper extends BaseMapper<AssessmentItem> {
}
