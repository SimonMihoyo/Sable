package me.kirara.sable.discovery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.discovery.entity.ScanTask;
import org.apache.ibatis.annotations.Mapper;

/** ScanTask 数据访问层。 */
@Mapper
public interface ScanTaskMapper extends BaseMapper<ScanTask> {
}
