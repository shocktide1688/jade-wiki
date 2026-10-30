package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.wiki.entity.WikiAttachment;
import com.jade.wiki.entity.WikiItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.jade.wiki.service.WikiPermissionService.Actor;

@ApplicationScoped
public class WikiAttachmentService {

    /** 允许上传的后缀（图片 + 常见文档, 其余拒绝） */
    private static final List<String> ALLOWED_SUFFIX = Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "zip");

    @ConfigProperty(name = "jade.wiki.upload-dir", defaultValue = "./data/uploads")
    String uploadDir;

    @Inject
    WikiPermissionService permission;

    @Inject
    WikiShareService shareService;

    @Transactional
    public WikiAttachment upload(Actor actor, Long itemId, String originalName,
                                 String contentType, InputStream in) {
        WikiItem item = null;
        if (itemId != null) {
            item = permission.requireItem(itemId);
            permission.requireEditable(item, actor);
        }

        String safeName = originalName == null ? "file" : originalName;
        int dot = safeName.lastIndexOf('.');
        String suffix = dot >= 0 ? safeName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_SUFFIX.contains(suffix)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的文件类型: " + suffix);
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        Path dir = Path.of(uploadDir, dateDir);
        Path target = dir.resolve(storedName);
        try {
            Files.createDirectories(dir);
            long size = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            WikiAttachment att = new WikiAttachment();
            att.itemId = itemId;
            att.fileName = storedName;
            att.originalName = safeName;
            att.fileSuffix = suffix;
            att.fileSize = size;
            att.contentType = contentType;
            att.storagePath = target.toString();
            att.uploaderId = actor.userId();
            att.uploaderName = actor.username();
            att.persist();
            return att;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    /** 下载鉴权: 公开项目/持分享 token/项目成员 可读; 未关联项目的附件要求登录(由调用方控制) */
    public WikiAttachment requireDownloadable(Long attachmentId, Actor actor, String shareToken) {
        WikiAttachment att = WikiAttachment.findById(attachmentId);
        if (att == null) {
            throw new BizException(ResultCode.NOT_FOUND, "附件不存在");
        }
        if (att.itemId == null) {
            return att;
        }
        WikiItem item = permission.requireItem(att.itemId);
        if ("public".equals(item.visibility)) {
            return att;
        }
        if (shareService.isValidToken(item, shareToken)) {
            return att;
        }
        if (actor != null && permission.roleOf(item, actor) >= 0) {
            return att;
        }
        throw new BizException(ResultCode.FORBIDDEN, "无权访问该附件");
    }

    public Path resolve(WikiAttachment att) {
        return Path.of(att.storagePath);
    }
}
