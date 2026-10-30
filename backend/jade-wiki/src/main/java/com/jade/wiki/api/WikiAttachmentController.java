package com.jade.wiki.api;

import com.jade.common.api.R;
import com.jade.common.exception.BizException;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.entity.WikiAttachment;
import com.jade.wiki.service.WikiAttachmentService;
import com.jade.wiki.service.WikiPermissionService;
import com.jade.wiki.service.WikiShareService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 附件/图片上传与下载（图片粘贴上传走这里）
 */
@Path("/api/v1/wiki/attachments")
@Tag(name = "Wiki 附件")
public class WikiAttachmentController {

    @Inject
    WikiAttachmentService attachmentService;

    @Inject
    WikiPermissionService permissionService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Authenticated
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "上传附件/图片")
    public R<WikiDtos.AttachmentView> upload(@RestForm("file") FileUpload file,
                                             @RestForm("itemId") Long itemId) {
        var actor = permissionService.actor(jwt);
        if (file == null) {
            return R.fail(4000, "缺少文件");
        }
        try (var in = Files.newInputStream(file.uploadedFile())) {
            WikiAttachment att = attachmentService.upload(actor, itemId,
                    file.fileName(), file.contentType(), in, file.size());
            return R.ok(toView(att));
        } catch (IOException e) {
            return R.fail(5000, "文件读取失败");
        }
    }

    @GET
    @Path("/{attachmentId}/download")
    @PermitAll
    @Operation(summary = "下载附件(公开项目匿名可读, 私密项目需分享token或成员)")
    public Response download(@PathParam("attachmentId") Long attachmentId,
                             @HeaderParam(WikiShareService.TOKEN_HEADER) String token) {
        // 匿名/登录双通道: 有 JWT 就解析成 Actor 参与成员判定
        WikiPermissionService.Actor actor = null;
        try {
            if (jwt.getSubject() != null) {
                actor = permissionService.actor(jwt);
            }
        } catch (Exception ignored) {
            // 匿名请求
        }
        WikiAttachment att = attachmentService.requireDownloadable(attachmentId, actor, token);
        String encoded = URLEncoder.encode(
                att.originalName == null ? att.fileName : att.originalName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        try {
            return Response.ok(attachmentService.open(att))
                    .header("Content-Disposition", "attachment; filename*=UTF-8''" + encoded)
                    .header("Content-Type", att.contentType == null
                            ? MediaType.APPLICATION_OCTET_STREAM : att.contentType)
                    .build();
        } catch (BizException e) {
            throw e; // 附件不存在/无权限 → 全局异常mapper
        } catch (RuntimeException e) {
            return Response.status(404).build();
        }
    }

    static WikiDtos.AttachmentView toView(WikiAttachment att) {
        return new WikiDtos.AttachmentView(att.id, att.itemId, att.originalName,
                att.fileSuffix, att.fileSize, att.contentType,
                "/api/v1/wiki/attachments/" + att.id + "/download");
    }
}
