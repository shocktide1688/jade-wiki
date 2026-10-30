package com.jade.ldapclient.config;

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
 * LDAP 认证源配置 (单行, id=1)
 *
 * bind 密码 AES-256-GCM 加密存储; 对接任意标准 LDAPv3 服务
 * (含 jade-ldap: ldap://localhost:1389, base dn dc=jade,dc=local)。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "sys_ldap_config")
public class LdapConfig extends PanacheEntityBase {

    public static final long SINGLETON_ID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 如 ldap://localhost:1389 或 ldaps://host:1636 */
    @Column(name = "ldap_url", nullable = false, length = 300)
    public String ldapUrl = "";

    /** 用户搜索基础 DN, 如 dc=jade,dc=local */
    @Column(name = "base_dn", length = 300)
    public String baseDn = "";

    /** 绑定账号 DN (用于搜索), 如 cn=admin,dc=jade,dc=local */
    @Column(name = "bind_dn", length = 300)
    public String bindDn = "";

    /** 绑定密码 (加密存储) */
    @Encrypted
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bind_password", length = 512)
    public String bindPassword = "";

    /** 用户搜索过滤器, {0} 占位用户名, 默认 (uid={0}) */
    @Column(name = "user_search_filter", length = 300)
    public String userSearchFilter = "(uid={0})";

    /** 认证成功后自动创建本地 SysUser (auth_source=ldap) */
    @Column(name = "auto_create_user", nullable = false)
    public Boolean autoCreateUser = true;

    @Column(nullable = false)
    public Boolean enabled = false;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public static LdapConfig singleton() {
        LdapConfig cfg = findById(SINGLETON_ID);
        if (cfg == null) {
            cfg = new LdapConfig();
            cfg.id = SINGLETON_ID;
            cfg.userSearchFilter = "(uid={0})";
            cfg.autoCreateUser = true;
        }
        return cfg;
    }
}
