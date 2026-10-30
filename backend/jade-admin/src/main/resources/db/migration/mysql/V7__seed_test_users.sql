-- =====================================================
-- V7: 测试用户 + 业务数据 (MySQL 方言)
-- =====================================================
-- 密码统一: admin123
-- MySQL: INSERT IGNORE 替代 ON CONFLICT; 无表的 SELECT 补 FROM DUAL
-- =====================================================

-- 1. 业务演示租户 (供 tenant 角色用)
INSERT IGNORE INTO sys_tenant (id, code, name, status) VALUES (3, 'demo', '演示租户', 1);

-- 2. 测试用户（密码都是 admin123）
INSERT IGNORE INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
VALUES ('user', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '测试用户', 'user@jade.local', 1, 3, FALSE);

INSERT IGNORE INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
VALUES ('tenant', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '租户管理员', 'tenant@jade.local', 1, 3, FALSE);

-- 3. 用户-角色关联
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, 2 FROM sys_user u WHERE u.username = 'user';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, 3 FROM sys_user u WHERE u.username = 'tenant';

-- 4. 用户-部门关联
INSERT IGNORE INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 4, TRUE FROM sys_user u WHERE u.username = 'user';

INSERT IGNORE INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 4, TRUE FROM sys_user u WHERE u.username = 'tenant';

-- 5. 演示项目
INSERT INTO sys_project (name, description, tenant_id, deleted)
SELECT '演示项目 A', '这是一个测试项目, 演示多租户隔离', 3, FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_project WHERE name = '演示项目 A' AND tenant_id = 3);

INSERT INTO sys_project (name, description, tenant_id, deleted)
SELECT '演示项目 B', '第二个测试项目, 展示 admin 看到的跨租户视图', 3, FALSE FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_project WHERE name = '演示项目 B' AND tenant_id = 3);
