package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/** Tenant 数据访问层。 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
