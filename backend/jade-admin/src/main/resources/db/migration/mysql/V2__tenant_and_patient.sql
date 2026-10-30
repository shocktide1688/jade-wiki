-- =====================================================
-- V2: 多租户 + 病人信息（字段加密演示）(MySQL 方言)
-- =====================================================

-- 租户表
CREATE TABLE sys_tenant (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_sys_tenant_status ON sys_tenant(status);

-- 项目表（演示多租户隔离）
CREATE TABLE sys_project (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_sys_project_tenant ON sys_project(tenant_id);
CREATE INDEX idx_sys_project_created ON sys_project(created_at);

-- 病人表（演示字段加密）
CREATE TABLE sys_patient (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    id_card     VARCHAR(512),
    phone       VARCHAR(512),
    diagnosis   VARCHAR(256),
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

-- 初始数据
INSERT INTO sys_tenant (code, name) VALUES
    ('t_001', '租户 A'),
    ('t_002', '租户 B');

INSERT INTO sys_project (tenant_id, name, description) VALUES
    (1, 'A 的项目 1', '这是 A 的项目'),
    (2, 'B 的项目 1', '这是 B 的项目');
