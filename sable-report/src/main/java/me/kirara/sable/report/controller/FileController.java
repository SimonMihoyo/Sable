package me.kirara.sable.report.controller;

import me.kirara.sable.common.R;
import me.kirara.sable.report.entity.Attachment;
import me.kirara.sable.report.service.AttachmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文件接口 — /files */
@RestController
@RequestMapping("/files")
public class FileController {

    private final AttachmentService attachmentService;

    public FileController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/upload")
    public R<Attachment> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam String bizType,
                                @RequestParam Long bizId) {
        Attachment attachment = new Attachment();
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileSize(file.getSize());
        attachment.setContentType(file.getContentType());
        // TODO 上传对象存储，回填 storage_path 与 checksum
        attachmentService.save(attachment);
        return R.ok(attachment);
    }

    @GetMapping("/{fileId}/download")
    public R<String> download(@PathVariable Long fileId) {
        // TODO 返回对象存储预签名下载地址
        return R.ok();
    }
}
