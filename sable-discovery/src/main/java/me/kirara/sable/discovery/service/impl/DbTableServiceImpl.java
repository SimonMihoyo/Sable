package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.discovery.entity.DbTable;
import me.kirara.sable.discovery.mapper.DbTableMapper;
import me.kirara.sable.discovery.service.DbTableService;
import org.springframework.stereotype.Service;

/** DbTable 业务服务实现。 */
@Service
public class DbTableServiceImpl extends ServiceImpl<DbTableMapper, DbTable> implements DbTableService {
}
