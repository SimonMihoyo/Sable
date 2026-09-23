package me.kirara.sable.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.alert.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;

/** AlertRule 数据访问层。 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {
}
