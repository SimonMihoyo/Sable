package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** SysUser 数据访问层。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
