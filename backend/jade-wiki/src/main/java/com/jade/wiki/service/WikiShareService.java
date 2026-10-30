package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.wiki.entity.WikiItem;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 分享访问令牌: HMAC(itemId + 当前密码) 无状态生成。
 * 公开项目无需 token; 私密项目凭密码换 token, 密码一改旧 token 立即失效。
 */
@ApplicationScoped
public class WikiShareService {

    public static final String TOKEN_HEADER = "X-Share-Token";

    @ConfigProperty(name = "jade.jwt.secret")
    String secret;

    private byte[] secretBytes;

    @PostConstruct
    void init() {
        secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String viewToken(WikiItem item) {
        String material = item.id + ":" + (item.password == null ? "public" : item.password);
        return hmac(material);
    }

    public boolean isValidToken(WikiItem item, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                viewToken(item).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    /** 私密项目凭密码换取访问 token */
    public String verify(WikiItem item, String password) {
        if ("public".equals(item.visibility)) {
            return viewToken(item);
        }
        if (item.password == null || password == null || !item.password.equals(password)) {
            throw new BizException(ResultCode.FORBIDDEN, "访问密码错误");
        }
        return viewToken(item);
    }

    /** 浏览接口的统一访问校验: 公开项目放行, 否则要求有效 token */
    public void requireViewable(WikiItem item, String token) {
        if ("public".equals(item.visibility) || isValidToken(item, token)) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN, "该项目为私密项目, 需要访问密码");
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "生成访问令牌失败");
        }
    }
}
