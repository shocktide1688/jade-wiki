package com.jade.ldapclient.rest;

import com.jade.common.api.R;
import com.jade.ldapclient.config.LdapConfig;
import com.jade.ldapclient.service.LdapAuthenticator;
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
 * LDAP 认证源配置管理
 */
@Path("/api/v1/ldap")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "LDAP 配置")
public class LdapConfigController {

    public record LdapConfigView(String ldapUrl, String baseDn, String bindDn,
                                 String bindPasswordMasked, String userSearchFilter,
                                 Boolean autoCreateUser, Boolean enabled) {}

    public record LdapConfigSave(String ldapUrl, String baseDn, String bindDn,
                                 String bindPassword, String userSearchFilter,
                                 Boolean autoCreateUser, Boolean enabled) {}

    @Inject
    LdapAuthenticator authenticator;

    @GET
    @Path("/config")
    @RolesAllowed("admin")
    @Operation(summary = "读取 LDAP 配置 (密码掩码)")
    public R<LdapConfigView> get() {
        LdapConfig c = LdapConfig.singleton();
        String masked = c.bindPassword == null || c.bindPassword.isBlank() ? "" : "******";
        return R.ok(new LdapConfigView(c.ldapUrl, c.baseDn, c.bindDn, masked,
                c.userSearchFilter, c.autoCreateUser, c.enabled));
    }

    @PUT
    @Path("/config")
    @RolesAllowed("admin")
    @Operation(summary = "保存 LDAP 配置 (bind 密码留空 = 保留旧值)")
    @Transactional
    public R<LdapConfigView> save(LdapConfigSave req) {
        LdapConfig c = LdapConfig.singleton();
        c.ldapUrl = req.ldapUrl();
        c.baseDn = req.baseDn();
        c.bindDn = req.bindDn();
        if (req.bindPassword() != null && !req.bindPassword().isBlank()) {
            c.bindPassword = req.bindPassword();
        }
        c.userSearchFilter = req.userSearchFilter() == null || req.userSearchFilter().isBlank()
                ? "(uid={0})" : req.userSearchFilter();
        c.autoCreateUser = req.autoCreateUser();
        c.enabled = req.enabled();
        LdapConfig.persist(c);
        return R.ok(new LdapConfigView(c.ldapUrl, c.baseDn, c.bindDn, "******",
                c.userSearchFilter, c.autoCreateUser, c.enabled));
    }

    @POST
    @Path("/test")
    @RolesAllowed("admin")
    @Operation(summary = "测试连接 (bind + 读取 baseDn), 返回错误信息或 '连接成功'")
    public R<String> test(@QueryParam("username") String username,
                          @QueryParam("password") String password) {
        String error = authenticator.testConnection();
        if (error != null) {
            return R.ok("连接失败: " + error);
        }
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            boolean ok = authenticator.authenticateAndSync(username, password).isPresent();
            return R.ok(ok ? "连接成功, 用户 " + username + " 认证通过"
                           : "连接成功, 但用户 " + username + " 认证未通过");
        }
        return R.ok("连接成功");
    }
}
