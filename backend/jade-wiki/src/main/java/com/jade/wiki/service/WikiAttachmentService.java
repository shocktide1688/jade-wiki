package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.oss.core.OssTemplate;
import com.jade.wiki.entity.WikiAttachment;
import com.jade.wiki.entity.WikiItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Inject
    OssTemplate oss;

    @Inject
    WikiPermissionService permission;

    @Inject
    WikiShareService shareService;

    @Transactional
    public WikiAttachment upload(Actor actor, Long itemId, String originalName,
                                 String contentType, InputStream in, long size) {
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

        // 对象 key: yyyy/MM/uuid.ext — storagePath 只存 key, 读写走 OssTemplate (provider 可切 local/s3)
        String key = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + suffix;
        oss.upload(key, in, size, contentType);

        WikiAttachment att = new WikiAttachment();
        att.itemId = itemId;
        att.fileName = key.substring(key.lastIndexOf('/') + 1);
        att.originalName = safeName;
        att.fileSuffix = suffix;
        att.fileSize = size;
        att.contentType = contentType;
        att.storagePath = key;
        att.uploaderId = actor.userId();
        att.uploaderName = actor.username();
        att.persist();
        return att;
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

    /** 打开附件读取流: 新数据走 OssTemplate; 存量本地路径直接读磁盘(升级迁移前兼容) */
    public InputStream open(WikiAttachment att) {
        if (!isLegacyLocalPath(att.storagePath)) {
            if (!oss.exists(att.storagePath)) {
                throw new BizException(ResultCode.NOT_FOUND, "附件文件不存在");
            }
            return oss.open(att.storagePath);
        }
        Path legacy = Path.of(att.storagePath);
        if (!Files.isRegularFile(legacy)) {
            throw new BizException(ResultCode.NOT_FOUND, "附件文件不存在");
        }
        try {
            return Files.newInputStream(legacy);
        } catch (Exception e) {
            throw new BizException(ResultCode.NOT_FOUND, "附件文件不存在");
        }
    }

    /** 存量数据识别: 老版本把本地磁盘路径(绝对/./开头)存进 storage_path, 新版本只存对象 key */
    static boolean isLegacyLocalPath(String storagePath) {
        return storagePath.startsWith("/") || storagePath.startsWith("./") || storagePath.startsWith("../");
    }
}
