package com.jade.mcp.auth;

import com.jade.mcp.token.McpTokenService;
import com.jade.security.repository.SysUserRepository;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

/**
 * MCP PAT 认证机制 (HTTP 层)
 *
 * quarkus-mcp-server-http 的端点走 vert.x 路由而非 JAX-RS 管道,
 * ContainerRequestFilter 拦不到 —— 必须用 HttpAuthenticationMechanism。
 *
 * 客户端携带 X-Mcp-Token: <PAT>, 校验通过后构建 SecurityIdentity
 * (principal=username, attribute userId) 供 MCP 工具层读取。
 * 未携带头 → 匿名放行 (由 quarkus.http.auth.permission.* 的 authenticated 策略统一 401)。
 */
@ApplicationScoped
public class McpTokenAuthMechanism implements HttpAuthenticationMechanism {

    public static final String TOKEN_HEADER = "X-Mcp-Token";
    public static final String ATTR_USER_ID = "mcp.userId";

    @Inject
    McpTokenService tokenService;

    @Inject
    SysUserRepository userRepository;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext ctx, IdentityProviderManager identityProviderManager) {
        String token = ctx.request().getHeader(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            return Uni.createFrom().optional(Optional.empty());
        }
        // 令牌校验含 DB 查询: 必须切到 worker 线程, IO 线程禁止阻塞
        return Uni.createFrom().item(() -> {
            // worker 线程无 CDI request context, Panache 查询需手动激活
            var requestContext = io.quarkus.arc.Arc.container().requestContext();
            boolean activatedHere = false;
            if (!requestContext.isActive()) {
                requestContext.activate();
                activatedHere = true;
            }
            try {
                return doAuthenticate(token);
            } finally {
                if (activatedHere) {
                    requestContext.terminate();
                }
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /** 无效/过期令牌返回 null = 匿名, 由 authenticated 策略统一 401 (抛异常会被 JAX-RS 兜底 mapper 转 500) */
    private SecurityIdentity doAuthenticate(String token) {
        Optional<Long> userId = tokenService.verify(token);
        if (userId.isEmpty()) {
            return null;
        }
        var user = userRepository.findById(userId.get());
        String username = user == null ? "unknown" : user.username;
        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new Principal() {
                    @Override
                    public String getName() {
                        return username;
                    }
                })
                .addAttribute(ATTR_USER_ID, userId.get())
                .addCredential(new TokenCredential(token, "mcp-pat"))
                .addRole("mcp")
                .build();
        return identity;
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext ctx) {
        return Uni.createFrom().item(new ChallengeData(HttpResponseStatus.UNAUTHORIZED.code(), TOKEN_HEADER, ""));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        // 自行构建 identity, 不委托 IdentityProviderManager
        return Set.of();
    }
}
