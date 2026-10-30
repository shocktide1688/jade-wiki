-- =====================================================
-- V12: 删除"平台演示"菜单 (jade-wiki 产品不含 demo 模块)
-- =====================================================
DELETE FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE id IN (4, 120, 121));
DELETE FROM sys_menu WHERE id IN (4, 120, 121);
