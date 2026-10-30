-- =====================================================
-- V8: data-scope E2E 测试数据 (SQLite 方言, 完全幂等, 可重跑)
-- =====================================================

-- 0. 清理 testuser_* 污染
DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'testuser_%');
DELETE FROM sys_user_dept WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'testuser_%');
DELETE FROM sys_user WHERE username LIKE 'testuser_%';

-- 1. 修复 admin/user/tenant 的部门关联
DELETE FROM sys_user_dept WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('admin', 'user', 'tenant'));
INSERT INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 2, TRUE FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_dept WHERE user_id = u.id AND is_primary = TRUE);
INSERT INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 4, TRUE FROM sys_user u WHERE u.username IN ('user', 'tenant')
  AND NOT EXISTS (SELECT 1 FROM sys_user_dept WHERE user_id = u.id AND is_primary = TRUE);

-- 2. 业务用户: alice / bob / charlie / dave (全部密码 admin123)
INSERT INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
SELECT 'alice', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '研发 Alice', 'alice@jade.local', 1, 1, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'alice');
INSERT INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
SELECT 'bob', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '研发 Bob', 'bob@jade.local', 1, 1, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'bob');
INSERT INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
SELECT 'charlie', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '运营 Charlie', 'charlie@jade.local', 1, 1, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'charlie');
INSERT INTO sys_user (username, password, nickname, email, status, tenant_id, deleted)
SELECT 'dave', '$2a$10$Z2XvRhbC0LdTpvdA5M3bVOzIy33PCL6ZdxqyKfGyJO2GMN8j7jqSe', '运营 Dave', 'dave@jade.local', 1, 3, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'dave');

-- 3. 把新用户挂到部门
INSERT INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 2, TRUE FROM sys_user u WHERE u.username IN ('alice', 'bob')
  AND NOT EXISTS (SELECT 1 FROM sys_user_dept WHERE user_id = u.id AND is_primary = TRUE);
INSERT INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 4, TRUE FROM sys_user u WHERE u.username = 'charlie'
  AND NOT EXISTS (SELECT 1 FROM sys_user_dept WHERE user_id = u.id AND is_primary = TRUE);
INSERT INTO sys_user_dept (user_id, dept_id, is_primary)
SELECT u.id, 4, TRUE FROM sys_user u WHERE u.username = 'dave'
  AND NOT EXISTS (SELECT 1 FROM sys_user_dept WHERE user_id = u.id AND is_primary = TRUE);

-- 4. 给新用户分角色 (idempotent: 先删后插, 保证 dave 是 tenant 角色)
DELETE FROM sys_user_role
WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('alice', 'bob', 'charlie', 'dave'));
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, 2 FROM sys_user u WHERE u.username IN ('alice', 'bob', 'charlie');
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, 3 FROM sys_user u WHERE u.username = 'dave';

-- 5. 产品一组子部门 (用于未来 DEPT_AND_CHILD 扩展)
INSERT INTO sys_dept (tenant_id, parent_id, dept_name, dept_code, sort_order, status, deleted)
SELECT 1, 3, '产品一组', 'PD-1', 1, 1, FALSE
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE dept_name = '产品一组');
