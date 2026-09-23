package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.discovery.entity.DbSchema;
import me.kirara.sable.discovery.mapper.DbSchemaMapper;
import me.kirara.sable.discovery.service.DbSchemaService;
import org.springframework.stereotype.Service;

/** DbSchema 业务服务实现。 */
@Service
public class DbSchemaServiceImpl extends ServiceImpl<DbSchemaMapper, DbSchema> implements DbSchemaService {
}
