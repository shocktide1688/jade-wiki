-- =====================================================
-- V13: MCP 个人访问令牌 (PostgreSQL)
-- =====================================================

CREATE TABLE mcp_token (
    id          BIGSERIAL    PRIMARY KEY,
    token       VARCHAR(80)  NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL,
    remark      VARCHAR(255),
    expires_at  TIMESTAMPTZ,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_mcp_token_user  ON mcp_token(user_id);
CREATE INDEX idx_mcp_token_token ON mcp_token(token);
