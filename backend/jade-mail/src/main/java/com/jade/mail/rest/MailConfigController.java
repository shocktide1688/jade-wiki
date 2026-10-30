package com.jade.mail.rest;

import com.jade.common.api.R;
import com.jade.mail.entity.MailConfig;
import com.jade.mail.service.MailService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 邮件配置管理
 */
@Path("/api/v1/mail")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "邮件配置")
public class MailConfigController {

    public record MailConfigView(String host, Integer port, String username,
                                 String passwordMasked, String fromAddress,
                                 String encryptType, Boolean enabled) {}

    public record MailConfigSave(String host, Integer port, String username,
                                 String password, String fromAddress,
                                 String encryptType, Boolean enabled) {}

    @Inject
    MailService mailService;

    @GET
    @Path("/config")
    @RolesAllowed("admin")
    @Operation(summary = "读取邮件配置 (密码掩码)")
    public R<MailConfigView> get() {
        MailConfig c = mailService.load();
        String masked = c.password == null || c.password.isBlank() ? ""
                : "******";
        return R.ok(new MailConfigView(c.host, c.port, c.username, masked,
                c.fromAddress, c.encryptType, c.enabled));
    }

    @PUT
    @Path("/config")
    @RolesAllowed("admin")
    @Operation(summary = "保存邮件配置 (密码留空 = 保留旧值)")
    @Transactional
    public R<MailConfigView> save(MailConfigSave req) {
        MailConfig incoming = new MailConfig();
        incoming.host = req.host();
        incoming.port = req.port();
        incoming.username = req.username();
        incoming.fromAddress = req.fromAddress();
        incoming.encryptType = req.encryptType();
        incoming.enabled = req.enabled();
        MailConfig c = mailService.save(incoming, req.password());
        return R.ok(new MailConfigView(c.host, c.port, c.username, "******",
                c.fromAddress, c.encryptType, c.enabled));
    }

    @POST
    @Path("/test")
    @RolesAllowed("admin")
    @Operation(summary = "发送测试邮件")
    public R<String> test(@QueryParam("to") String to) {
        if (to == null || to.isBlank()) {
            return R.fail(4000, "请填写收件邮箱");
        }
        mailService.sendHtml(to, "Jade 邮件测试",
                "<h3>这是一封测试邮件</h3><p>来自 Jade Platform 邮件配置测试。收到即代表 SMTP 配置正确。</p>");
        return R.ok("测试邮件已发送至 " + to);
    }
}
