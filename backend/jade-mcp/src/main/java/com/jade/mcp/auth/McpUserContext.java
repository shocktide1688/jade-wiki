package com.jade.mcp.auth;

/**
 * MCP 请求上下文: 鉴权 filter 解析 PAT 后写入, @Tool 方法内读取
 */
public class McpUserContext {

    private static final ThreadLocal<UserInfo> CTX = new ThreadLocal<>();

    public record UserInfo(Long userId, String username) {}

    public static void set(Long userId, String username) {
        CTX.set(new UserInfo(userId, username));
    }

    public static Long getUserId() {
        UserInfo info = CTX.get();
        if (info == null) {
            throw new IllegalStateException("MCP 上下文缺失: 请求未经过 McpAuthFilter");
        }
        return info.userId();
    }

    public static UserInfo get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }
}
