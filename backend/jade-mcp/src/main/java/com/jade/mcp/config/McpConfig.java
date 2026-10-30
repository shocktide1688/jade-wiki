package com.jade.mcp.config;

import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * jade.mcp.* 配置读取
 *
 * 注: 刻意不用 @ConfigMapping —— 纯库模块不经过 Quarkus deployment 阶段,
 * mapping 注册依赖消费方构建环境, 直接读 ConfigProvider 最稳。
 */
public final class McpConfig {

    public static final String PATH_DEFAULT = "/mcp";

    private McpConfig() {
    }

    private static SmallRyeConfig cfg() {
        return ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
    }

    /** 总开关: false 时鉴权 filter 不拦截 (MCP 端点由 quarkus.mcp.server.http.* 控制) */
    public static boolean enabled() {
        return cfg().getOptionalValue("jade.mcp.enabled", Boolean.class).orElse(true);
    }

    /** MCP 端点路径前缀, 与 quarkus.mcp.server.http.root-path 保持一致 */
    public static String path() {
        return cfg().getOptionalValue("jade.mcp.path", String.class).orElse(PATH_DEFAULT);
    }
}
