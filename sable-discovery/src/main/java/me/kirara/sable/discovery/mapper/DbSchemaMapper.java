package me.kirara.sable.discovery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.discovery.entity.DbSchema;
import org.apache.ibatis.annotations.Mapper;

/** DbSchema 数据访问层。 */
@Mapper
public interface DbSchemaMapper extends BaseMapper<DbSchema> {
}
