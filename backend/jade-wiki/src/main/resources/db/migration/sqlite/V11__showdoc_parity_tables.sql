-- =====================================================
-- V11: ShowDoc 功能对齐表 (SQLite 方言)
-- =====================================================

CREATE TABLE wiki_template (
    id               INTEGER      PRIMARY KEY,
    tenant_id        BIGINT,
    template_name    VARCHAR(128) NOT NULL,
    template_content TEXT,
    is_builtin       INTEGER     NOT NULL DEFAULT 0,  -- 1=系统内置
    creator_id       BIGINT,
    creator_name     VARCHAR(64),
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 系统内置模板（幂等, 模板正文用真实换行, SQLite 不解析 \n 转义）
INSERT INTO wiki_template (template_name, template_content, is_builtin, creator_id, creator_name)
SELECT 'API 接口文档', '# 接口名称

## 接口地址

`POST /api/v1/xxx`

## 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | long | 是 | 资源ID |

## 响应示例

```json
{
  "code": 0,
  "data": {}
}
```', 1, NULL, 'system'
WHERE NOT EXISTS (SELECT 1 FROM wiki_template WHERE template_name = 'API 接口文档' AND is_builtin = 1);

INSERT INTO wiki_template (template_name, template_content, is_builtin, creator_id, creator_name)
SELECT '数据表文档', '# 表名

## 字段说明

| 字段 | 类型 | 允许空 | 默认值 | 说明 |
|---|---|---|---|---|
| id | bigint | 否 | - | 主键 |

## 索引

- UNIQUE(username)', 1, NULL, 'system'
WHERE NOT EXISTS (SELECT 1 FROM wiki_template WHERE template_name = '数据表文档' AND is_builtin = 1);

CREATE TABLE wiki_attachment (
    id            INTEGER      PRIMARY KEY,
    item_id       BIGINT,                           -- 归属项目（可空=未关联）
    file_name     VARCHAR(255) NOT NULL,            -- 存储文件名
    original_name VARCHAR(255),                     -- 原始文件名
    file_suffix   VARCHAR(32),
    file_size     BIGINT,
    content_type  VARCHAR(128),
    storage_path  VARCHAR(512) NOT NULL,
    uploader_id   BIGINT,
    uploader_name VARCHAR(64),
    created_at    TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wiki_attachment_item ON wiki_attachment(item_id);

CREATE TABLE wiki_api_key (
    id             INTEGER      PRIMARY KEY,
    item_id        BIGINT       NOT NULL,
    api_key        VARCHAR(64)  NOT NULL UNIQUE,
    api_secret     VARCHAR(128) NOT NULL,
    remark         VARCHAR(255),
    created_by     BIGINT,
    created_by_name VARCHAR(64),
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wiki_apikey_item ON wiki_api_key(item_id);
