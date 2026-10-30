-- =====================================================
-- V9: Wiki 核心表 (SQLite 方言)
-- =====================================================

CREATE TABLE wiki_item (
    id               INTEGER      PRIMARY KEY,
    tenant_id        BIGINT,
    item_name        VARCHAR(128) NOT NULL,
    item_description VARCHAR(512),
    visibility       VARCHAR(20)  NOT NULL DEFAULT 'private',  -- public/private
    password         VARCHAR(512),                             -- AES-256-GCM 加密存储
    item_type        VARCHAR(20)  NOT NULL DEFAULT 'regular',  -- regular/single
    creator_id       BIGINT       NOT NULL,
    creator_name     VARCHAR(64),
    is_archive       BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wiki_item_tenant  ON wiki_item(tenant_id);
CREATE INDEX idx_wiki_item_creator ON wiki_item(creator_id);

CREATE TABLE wiki_item_member (
    id           INTEGER     PRIMARY KEY,
    item_id      BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    member_group INTEGER    NOT NULL DEFAULT 1,  -- 0=所有者 1=可编辑 2=只读
    created_at   TEXT        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (item_id, user_id)
);

CREATE INDEX idx_wiki_member_user ON wiki_item_member(user_id);

CREATE TABLE wiki_catalog (
    id         INTEGER      PRIMARY KEY,
    item_id    BIGINT       NOT NULL,
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    cat_name   VARCHAR(128) NOT NULL,
    s_number   INT          NOT NULL DEFAULT 0,
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wiki_catalog_item ON wiki_catalog(item_id);

CREATE TABLE wiki_page (
    id           INTEGER      PRIMARY KEY,
    item_id      BIGINT       NOT NULL,
    cat_id       BIGINT,                          -- NULL = 未分类
    page_title   VARCHAR(255) NOT NULL,
    page_content TEXT,                            -- Markdown 正文
    s_number     INT          NOT NULL DEFAULT 0,
    author_id    BIGINT,
    author_name  VARCHAR(64),
    version      BIGINT       NOT NULL DEFAULT 0, -- 乐观锁
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wiki_page_item ON wiki_page(item_id);
CREATE INDEX idx_wiki_page_cat  ON wiki_page(cat_id);

CREATE TABLE wiki_page_history (
    id           INTEGER      PRIMARY KEY,
    page_id      BIGINT       NOT NULL,
    page_title   VARCHAR(255),
    page_content TEXT,
    author_id    BIGINT,
    author_name  VARCHAR(64),
    created_at   TEXT         NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wiki_history_page ON wiki_page_history(page_id);
