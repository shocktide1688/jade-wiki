-- =====================================================
-- V15: LDAP 认证源配置 (MySQL 方言)
-- =====================================================

CREATE TABLE sys_ldap_config (
    id                 BIGINT       AUTO_INCREMENT PRIMARY KEY,
    ldap_url           VARCHAR(300) NOT NULL,
    base_dn            VARCHAR(300),
    bind_dn            VARCHAR(300),
    bind_password      VARCHAR(512),
    user_search_filter VARCHAR(300) NOT NULL DEFAULT '(uid={0})',
    auto_create_user   BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled            BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

INSERT IGNORE INTO sys_ldap_config (id, ldap_url, base_dn, user_search_filter, auto_create_user, enabled)
VALUES (1, '', '', '(uid={0})', TRUE, FALSE);

ALTER TABLE sys_user ADD COLUMN auth_source VARCHAR(20) NOT NULL DEFAULT 'local';

-- 菜单: 系统管理 -> LDAP 配置 (id=141)
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort_order, visible, status, is_cache)
VALUES (141, 1, 'LDAP 配置', 'C', 'ldap', 'system/ldap/index', 'key', 'system:ldap:config', 8, 1, 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 141;
