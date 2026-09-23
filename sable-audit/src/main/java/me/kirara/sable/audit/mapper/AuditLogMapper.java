package me.kirara.sable.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.kirara.sable.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/** AuditLog 数据访问层。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
