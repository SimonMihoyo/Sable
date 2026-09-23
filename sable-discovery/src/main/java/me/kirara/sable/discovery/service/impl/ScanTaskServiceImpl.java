package me.kirara.sable.discovery.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.discovery.entity.ScanTask;
import me.kirara.sable.discovery.mapper.ScanTaskMapper;
import me.kirara.sable.discovery.service.ScanTaskService;
import org.springframework.stereotype.Service;

/** ScanTask 业务服务实现。 */
@Service
public class ScanTaskServiceImpl extends ServiceImpl<ScanTaskMapper, ScanTask> implements ScanTaskService {
}
