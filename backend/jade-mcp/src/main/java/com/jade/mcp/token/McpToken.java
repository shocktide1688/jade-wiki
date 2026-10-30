package com.jade.mcp.token;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MCP 个人访问令牌 (PAT)
 *
 * AI 客户端 (Claude/ZCode/Cursor) 通过 X-Mcp-Token 头携带此令牌访问 /mcp 端点,
 * 令牌解析出 userId 后走各产品的权限体系 —— AI 可见范围永远不超过所属用户。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "mcp_token")
public class McpToken extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** jmt_ 前缀 + 32 位随机 hex */
    @Column(nullable = false, unique = true, length = 80)
    public String token;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(length = 255)
    public String remark;

    /** null = 永不过期 */
    @Column(name = "expires_at")
    public LocalDateTime expiresAt;

    @Column(nullable = false)
    public Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    public boolean expired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public static Optional<McpToken> findActiveByToken(String token) {
        return find("token = ?1 and deleted = false", token).firstResultOptional()
                .map(t -> (McpToken) t)
                .filter(t -> !t.expired());
    }

    public static List<McpToken> listByUser(Long userId) {
        return list("userId = ?1 and deleted = false", Sort.by("id").descending(), userId);
    }
}
