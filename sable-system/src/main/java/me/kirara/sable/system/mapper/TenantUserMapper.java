package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.TenantUser;
import org.apache.ibatis.annotations.Mapper;

/** TenantUser 数据访问层。 */
@Mapper
public interface TenantUserMapper extends BaseMapper<TenantUser> {
}
