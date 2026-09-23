package me.kirara.sable.discovery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.discovery.entity.DataSource;
import org.apache.ibatis.annotations.Mapper;

/** DataSource 数据访问层。 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSource> {
}
