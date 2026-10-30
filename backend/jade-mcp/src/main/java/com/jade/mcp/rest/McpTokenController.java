package com.jade.mcp.rest;

import com.jade.common.api.R;
import com.jade.mcp.token.McpToken;
import com.jade.mcp.token.McpTokenService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * MCP 访问令牌管理 (各产品前端复用)
 */
@Path("/api/v1/mcp/tokens")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "MCP 访问令牌")
public class McpTokenController {

    public record TokenCreateRequest(String remark) {}

    public record TokenView(Long id, String maskedToken, String remark, String createdAt) {}

    public record TokenCreatedView(Long id, String token, String remark) {}

    @Inject
    McpTokenService tokenService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(summary = "我的令牌列表(掩码)")
    public R<List<TokenView>> list() {
        Long userId = Long.valueOf(jwt.getSubject());
        return R.ok(tokenService.listByUser(userId).stream()
                .map(t -> new TokenView(t.id, mask(t.token), t.remark,
                        t.createdAt == null ? null : t.createdAt.toString()))
                .toList());
    }

    @POST
    @Operation(summary = "生成新令牌(明文仅此一次返回)")
    public R<TokenCreatedView> create(TokenCreateRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        McpToken t = tokenService.generate(userId, request == null ? null : request.remark(), null);
        return R.ok(new TokenCreatedView(t.id, t.token, t.remark));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "吊销令牌")
    public R<Void> revoke(@PathParam("id") Long id) {
        Long userId = Long.valueOf(jwt.getSubject());
        if (!tokenService.revoke(userId, id)) {
            return R.fail(4004, "令牌不存在");
        }
        return R.ok();
    }

    private String mask(String token) {
        if (token == null || token.length() < 12) {
            return "****";
        }
        return token.substring(0, 8) + "****" + token.substring(token.length() - 4);
    }
}
