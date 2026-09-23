package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.discovery.entity.DbColumn;
import me.kirara.sable.discovery.mapper.DbColumnMapper;
import me.kirara.sable.discovery.service.DbColumnService;
import org.springframework.stereotype.Service;

/** DbColumn 业务服务实现。 */
@Service
public class DbColumnServiceImpl extends ServiceImpl<DbColumnMapper, DbColumn> implements DbColumnService {
}
