package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.discovery.entity.DataSource;
import me.kirara.sable.discovery.mapper.DataSourceMapper;
import me.kirara.sable.discovery.service.DataSourceService;
import org.springframework.stereotype.Service;

/** DataSource 业务服务实现。 */
@Service
public class DataSourceServiceImpl extends ServiceImpl<DataSourceMapper, DataSource> implements DataSourceService {
}
