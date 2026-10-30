-- =====================================================
-- V6: 给 sys_project 加 deleted 字段 (MySQL 方言)
-- =====================================================
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS, Flyway 保证只执行一次
ALTER TABLE sys_project ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
