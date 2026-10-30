package com.jade.mail.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.mail.entity.MailConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;

import java.util.Properties;

/**
 * 邮件服务: SMTP 配置存库, 每次发信动态构建 Session (改配置即生效)
 */
@ApplicationScoped
public class MailService {

    @Transactional
    public MailConfig load() {
        return MailConfig.singleton();
    }

    @Transactional
    public MailConfig save(MailConfig incoming, String passwordOverride) {
        MailConfig cfg = MailConfig.singleton();
        cfg.host = incoming.host;
        cfg.port = incoming.port == null ? 465 : incoming.port;
        cfg.username = incoming.username;
        cfg.fromAddress = incoming.fromAddress;
        cfg.encryptType = incoming.encryptType == null ? "SSL" : incoming.encryptType;
        cfg.enabled = incoming.enabled;
        // 密码: 前端传空/掩码 = 保留旧值
        if (passwordOverride != null && !passwordOverride.isBlank()) {
            cfg.password = passwordOverride;
        }
        MailConfig.persist(cfg);
        return cfg;
    }

    /** 发送 HTML 邮件; 未启用/配置不全抛业务异常 */
    public void sendHtml(String to, String subject, String html) {
        MailConfig cfg = load();
        if (!Boolean.TRUE.equals(cfg.enabled)) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "邮件功能未启用, 请先在系统管理-邮件配置中开启");
        }
        if (cfg.host == null || cfg.host.isBlank()) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "SMTP 服务器未配置");
        }
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", cfg.host);
            props.put("mail.smtp.port", String.valueOf(cfg.port));
            props.put("mail.smtp.auth", cfg.username != null && !cfg.username.isBlank());
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");
            switch (cfg.encryptType == null ? "SSL" : cfg.encryptType) {
                case "SSL" -> props.put("mail.smtp.ssl.enable", "true");
                case "STARTTLS" -> props.put("mail.smtp.starttls.enable", "true");
                default -> props.put("mail.smtp.ssl.enable", "false");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(cfg.username, cfg.password);
                }
            });

            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(cfg.fromAddress == null || cfg.fromAddress.isBlank()
                    ? cfg.username : cfg.fromAddress, true));
            msg.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(to, false));
            msg.setSubject(subject, "UTF-8");
            msg.setContent(html, "text/html;charset=UTF-8");
            Transport.send(msg);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            Throwable cur = e;
            while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
            throw new BizException(ResultCode.BUSINESS_ERROR, "邮件发送失败: " + cur.getMessage());
        }
    }
}
