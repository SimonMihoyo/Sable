package me.kirara.sable.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.system.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/** SysAuditLog 数据访问层。 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
