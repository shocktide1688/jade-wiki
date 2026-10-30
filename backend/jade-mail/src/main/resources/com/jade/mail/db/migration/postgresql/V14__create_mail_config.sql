-- =====================================================
-- V14: SMTP 邮件配置 (PostgreSQL) — 单行表 id=1
-- =====================================================

CREATE TABLE sys_mail_config (
    id            BIGSERIAL    PRIMARY KEY,
    host          VARCHAR(200) NOT NULL,
    port          INTEGER      NOT NULL DEFAULT 465,
    username      VARCHAR(200),
    password      VARCHAR(512),          -- AES-256-GCM 密文
    from_address  VARCHAR(200),
    encrypt_type  VARCHAR(20)  NOT NULL DEFAULT 'SSL',
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO sys_mail_config (id, host, port, encrypt_type, enabled)
VALUES (1, '', 465, 'SSL', FALSE)
ON CONFLICT (id) DO NOTHING;

-- 菜单: 系统管理 -> 邮件配置 (id=140)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort_order, visible, status, is_cache)
VALUES (140, 1, '邮件配置', 'C', 'mail', 'system/mail/index', 'message', 'system:mail:config', 7, 1, 1, 0)
ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 140
ON CONFLICT DO NOTHING;
