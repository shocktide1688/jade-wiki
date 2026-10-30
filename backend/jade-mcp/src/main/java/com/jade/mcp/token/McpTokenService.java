package com.jade.mcp.token;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * MCP 访问令牌服务: 生成 / 校验 / 吊销
 *
 * 令牌明文只在生成时返回一次 (GitHub PAT 惯例), 服务端按明文等值查询;
 * 自托管场景下可在 UI 随时吊销, 风险可接受, 后续可升级为摘要存储 + 掩码展示。
 */
@ApplicationScoped
public class McpTokenService {

    /**
     * 为用户生成新令牌 (jmt_ + 48 位随机 hex), 明文仅此一次返回
     */
    @Transactional
    public McpToken generate(Long userId, String remark, LocalDateTime expiresAt) {
        McpToken token = new McpToken();
        token.token = "jmt_" + randomHex(24);
        token.userId = userId;
        token.remark = remark;
        token.expiresAt = expiresAt;
        token.persist();
        return token;
    }

    /** 校验令牌, 有效返回所属 userId */
    @Transactional
    public Optional<Long> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return McpToken.findActiveByToken(token).map(t -> t.userId);
    }

    public List<McpToken> listByUser(Long userId) {
        return McpToken.listByUser(userId);
    }

    public Optional<McpToken> findByUserAndId(Long userId, Long id) {
        return McpToken.find("id = ?1 and userId = ?2 and deleted = false", id, userId).firstResultOptional()
                .map(t -> (McpToken) t);
    }

    @Transactional
    public boolean revoke(Long userId, Long id) {
        return findByUserAndId(userId, id).map(t -> {
            t.deleted = true;
            return true;
        }).orElse(false);
    }

    private String randomHex(int bytes) {
        // GraalVM native: static SecureRandom 会进 image heap (UnsupportedFeature), 每次创建
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buf)).substring(0, bytes * 2);
        } catch (NoSuchAlgorithmException e) {
            return HexFormat.of().formatHex(buf);
        }
    }
}
