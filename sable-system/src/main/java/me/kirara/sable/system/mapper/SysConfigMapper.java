package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/** SysConfig 数据访问层。 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
