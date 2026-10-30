-- =====================================================
-- V13: MCP 个人访问令牌 (MySQL 方言)
-- =====================================================

CREATE TABLE mcp_token (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    token       VARCHAR(80)  NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL,
    remark      VARCHAR(255),
    expires_at  DATETIME(6),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE INDEX idx_mcp_token_user  ON mcp_token(user_id);
CREATE INDEX idx_mcp_token_token ON mcp_token(token);
