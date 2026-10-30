-- =====================================================
-- V3: 安全加固 (SQLite 方言)
--   - sys_user 加 tenant_id
--   - 加默认 admin 的 BCrypt 密码（不再是明文）
-- =====================================================

-- 1. sys_user 加 tenant_id 列
ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT;

-- 2. admin 用户关联到默认租户（t_001 = 1）
UPDATE sys_user SET tenant_id = 1 WHERE username = 'admin' AND tenant_id IS NULL;

-- 3. 改 admin 密码为 BCrypt(admin123)
UPDATE sys_user
SET password = '$2a$10$SE9M6EdbRgl9k1Vt4v0hTeXY16zYXDwGIE7m8vlqqOr2EtEB5/C.G'
WHERE username = 'admin';

-- 4. 索引：按 tenant 查 user（SQLite 支持部分索引, 与 PG 版一致）
CREATE INDEX idx_sys_user_tenant ON sys_user(tenant_id) WHERE deleted = FALSE;
