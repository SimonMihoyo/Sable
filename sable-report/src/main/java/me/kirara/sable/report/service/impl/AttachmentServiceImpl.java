package me.kirara.sable.report.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.kirara.sable.report.entity.Attachment;
import me.kirara.sable.report.mapper.AttachmentMapper;
import me.kirara.sable.report.service.AttachmentService;
import org.springframework.stereotype.Service;

/** Attachment 业务服务实现。 */
@Service
public class AttachmentServiceImpl extends ServiceImpl<AttachmentMapper, Attachment> implements AttachmentService {
}
