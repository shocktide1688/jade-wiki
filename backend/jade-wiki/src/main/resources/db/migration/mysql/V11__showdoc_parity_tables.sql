-- =====================================================
-- V11: ShowDoc 功能对齐表 (MySQL 方言)
-- =====================================================

CREATE TABLE wiki_template (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT,
    template_name    VARCHAR(128) NOT NULL,
    template_content TEXT,
    is_builtin       INT     NOT NULL DEFAULT 0,  -- 1=系统内置
    creator_id       BIGINT,
    creator_name     VARCHAR(64),
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

INSERT IGNORE INTO wiki_template (template_name, template_content, is_builtin, creator_id, creator_name)
SELECT 'API 接口文档', '# 接口名称\n\n## 接口地址\n\n`POST /api/v1/xxx`\n\n## 请求参数\n\n| 参数名 | 类型 | 必填 | 说明 |\n|---|---|---|---|\n| id | long | 是 | 资源ID |\n\n## 响应示例\n\n```json\n{\n  "code": 0,\n  "data": {}\n}\n```', 1, NULL, 'system'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wiki_template WHERE template_name = 'API 接口文档' AND is_builtin = 1);

INSERT IGNORE INTO wiki_template (template_name, template_content, is_builtin, creator_id, creator_name)
SELECT '数据表文档', '# 表名\n\n## 字段说明\n\n| 字段 | 类型 | 允许空 | 默认值 | 说明 |\n|---|---|---|---|---|\n| id | bigint | 否 | - | 主键 |\n\n## 索引\n\n- UNIQUE(username)', 1, NULL, 'system'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM wiki_template WHERE template_name = '数据表文档' AND is_builtin = 1);

CREATE TABLE wiki_attachment (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    item_id       BIGINT,
    file_name     VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    file_suffix   VARCHAR(32),
    file_size     BIGINT,
    content_type  VARCHAR(128),
    storage_path  VARCHAR(512) NOT NULL,
    uploader_id   BIGINT,
    uploader_name VARCHAR(64),
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_wiki_attachment_item ON wiki_attachment(item_id);

CREATE TABLE wiki_api_key (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    item_id        BIGINT       NOT NULL,
    api_key        VARCHAR(64)  NOT NULL UNIQUE,
    api_secret     VARCHAR(128) NOT NULL,
    remark         VARCHAR(255),
    created_by     BIGINT,
    created_by_name VARCHAR(64),
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_wiki_apikey_item ON wiki_api_key(item_id);
