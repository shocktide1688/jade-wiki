package com.jade.mail.entity;

import com.jade.security.annotation.Encrypted;
import com.jade.security.crypto.EncryptedStringConverter;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * SMTP 邮件配置 (单行, id=1)
 *
 * 密码 AES-256-GCM 加密存储 (@Encrypted); 配置存库改完即生效, 无需重启。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "sys_mail_config")
public class MailConfig extends PanacheEntityBase {

    public static final long SINGLETON_ID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 200)
    public String host = "";

    @Column(nullable = false)
    public Integer port = 465;

    @Column(length = 200)
    public String username = "";

    /** SMTP 密码/授权码 (加密存储) */
    @Encrypted
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    public String password = "";

    /** 发件人地址 */
    @Column(name = "from_address", length = 200)
    public String fromAddress = "";

    /** 465=SSL, 587=STARTTLS, 25=无 */
    @Column(name = "encrypt_type", nullable = false, length = 20)
    public String encryptType = "SSL";

    @Column(nullable = false)
    public Boolean enabled = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public static MailConfig singleton() {
        MailConfig cfg = findById(SINGLETON_ID);
        if (cfg == null) {
            cfg = new MailConfig();
            cfg.id = SINGLETON_ID;
        }
        return cfg;
    }
}
