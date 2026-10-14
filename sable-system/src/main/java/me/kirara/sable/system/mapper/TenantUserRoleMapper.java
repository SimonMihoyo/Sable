package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.TenantUserRole;
import org.apache.ibatis.annotations.Mapper;

/** TenantUserRole 数据访问层。 */
@Mapper
public interface TenantUserRoleMapper extends BaseMapper<TenantUserRole> {
}
