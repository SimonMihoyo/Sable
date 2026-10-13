package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.PasswordHistory;
import org.apache.ibatis.annotations.Mapper;

/** PasswordHistory 数据访问层。 */
@Mapper
public interface PasswordHistoryMapper extends BaseMapper<PasswordHistory> {
}
