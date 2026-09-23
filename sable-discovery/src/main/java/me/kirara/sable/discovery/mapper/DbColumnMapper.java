package me.kirara.sable.discovery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.discovery.entity.DbColumn;
import org.apache.ibatis.annotations.Mapper;

/** DbColumn 数据访问层。 */
@Mapper
public interface DbColumnMapper extends BaseMapper<DbColumn> {
}
