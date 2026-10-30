-- =====================================================
-- V10: Wiki 菜单 (MySQL 方言)
-- =====================================================
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, perms, sort_order, visible, status, is_cache)
VALUES
  (200, 0,   '文档中心', 'M', 'wiki',      NULL,                   'documentation', NULL,              5, 1, 1, 0),
  (201, 200, '我的项目', 'C', 'items',     'wiki/items/index',     NULL, 'wiki:items:list',    1, 1, 1, 1),
  (202, 200, '编辑器',   'C', 'workbench', 'wiki/workbench/index', NULL, 'wiki:items:list',    2, 0, 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (200, 201, 202);
