package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/** Role 数据访问层。 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
