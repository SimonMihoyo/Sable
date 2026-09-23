package me.kirara.sable.discovery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.discovery.entity.DbTable;
import org.apache.ibatis.annotations.Mapper;

/** DbTable 数据访问层。 */
@Mapper
public interface DbTableMapper extends BaseMapper<DbTable> {
}
