-- =====================================================
-- Jade Admin 初始化脚本 (MySQL 方言)
-- =====================================================

-- 用户表
CREATE TABLE sys_user (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64),
    email       VARCHAR(128),
    phone       VARCHAR(32),
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

CREATE INDEX idx_sys_user_status ON sys_user(status);
CREATE INDEX idx_sys_user_created ON sys_user(created_at);

-- 角色表
CREATE TABLE sys_role (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(64)  NOT NULL,
    remark      VARCHAR(255),
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

-- 用户-角色关联
CREATE TABLE sys_user_role (
    user_id     BIGINT       NOT NULL,
    role_id     BIGINT       NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB;

-- 初始数据：默认管理员
-- 密码：admin123（BCrypt 加密）
INSERT INTO sys_user (username, password, nickname, email, status)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '超级管理员', 'admin@jade.com', 1);

-- 初始角色
INSERT INTO sys_role (code, name, remark) VALUES
    ('admin', '超级管理员', '系统最高权限'),
    ('user',  '普通用户',   '基础权限');

-- 绑定 admin -> admin
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'admin' AND r.code = 'admin';
