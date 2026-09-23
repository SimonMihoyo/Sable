package me.kirara.sable.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.alert.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;

/** AlertEvent 数据访问层。 */
@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {
}
