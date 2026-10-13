package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.TokenRevocation;
import org.apache.ibatis.annotations.Mapper;

/** TokenRevocation 数据访问层。 */
@Mapper
public interface TokenRevocationMapper extends BaseMapper<TokenRevocation> {
}
