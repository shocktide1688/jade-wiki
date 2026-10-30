package com.jade.wiki.api;

import com.jade.admin.aspect.Log;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.entity.WikiApiKey;
import com.jade.wiki.service.WikiExportService;
import com.jade.wiki.service.WikiItemService;
import com.jade.wiki.service.WikiOpenApiService;
import com.jade.wiki.service.WikiPermissionService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Wiki 项目管理
 */
@Path("/api/v1/wiki/items")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Wiki 项目")
public class WikiItemController {

    @Inject
    WikiItemService itemService;

    @Inject
    WikiPermissionService permissionService;

    @Inject
    WikiOpenApiService openApiService;

    @Inject
    WikiExportService exportService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(summary = "我的项目分页")
    public R<PageResult<WikiDtos.ItemView>> page(@QueryParam("keyword") String keyword,
                                                 @QueryParam("page") Integer page,
                                                 @QueryParam("size") Integer size) {
        return R.ok(itemService.page(actor(), keyword, orDefault(page, 1), orDefault(size, 10)));
    }

    @POST
    @Log(title = "文档管理", businessType = 1)
    @Operation(summary = "创建项目")
    public R<WikiDtos.ItemView> create(@Valid WikiDtos.ItemCreateRequest request) {
        return R.ok(itemService.create(actor(), request));
    }

    @GET
    @Path("/{itemId}")
    @Operation(summary = "项目详情")
    public R<WikiDtos.ItemView> detail(@PathParam("itemId") Long itemId) {
        return R.ok(itemService.detail(actor(), itemId));
    }

    @PUT
    @Path("/{itemId}")
    @Log(title = "文档管理", businessType = 2)
    @Operation(summary = "更新项目")
    public R<WikiDtos.ItemView> update(@PathParam("itemId") Long itemId,
                                       @Valid WikiDtos.ItemUpdateRequest request) {
        return R.ok(itemService.update(actor(), itemId, request));
    }

    @DELETE
    @Path("/{itemId}")
    @Log(title = "文档管理", businessType = 3)
    @Operation(summary = "删除项目(软删)")
    public R<Void> delete(@PathParam("itemId") Long itemId) {
        itemService.delete(actor(), itemId);
        return R.ok();
    }

    @GET
    @Path("/{itemId}/members")
    @Operation(summary = "项目成员列表")
    public R<List<WikiDtos.MemberView>> members(@PathParam("itemId") Long itemId) {
        return R.ok(itemService.members(actor(), itemId));
    }

    @POST
    @Path("/{itemId}/members")
    @Log(title = "文档管理", businessType = 1)
    @Operation(summary = "添加项目成员")
    public R<Void> addMember(@PathParam("itemId") Long itemId,
                             @Valid WikiDtos.MemberAddRequest request) {
        itemService.addMember(actor(), itemId, request);
        return R.ok();
    }

    @PUT
    @Path("/{itemId}/members/{memberId}")
    @Log(title = "文档管理", businessType = 2)
    @Operation(summary = "修改成员角色")
    public R<Void> updateMember(@PathParam("itemId") Long itemId,
                                @PathParam("memberId") Long memberId,
                                @Valid WikiDtos.MemberUpdateRequest request) {
        itemService.updateMember(actor(), itemId, memberId, request);
        return R.ok();
    }

    @DELETE
    @Path("/{itemId}/members/{memberId}")
    @Log(title = "文档管理", businessType = 3)
    @Operation(summary = "移除项目成员")
    public R<Void> removeMember(@PathParam("itemId") Long itemId,
                                @PathParam("memberId") Long memberId) {
        itemService.removeMember(actor(), itemId, memberId);
        return R.ok();
    }

    @GET
    @Path("/{itemId}/export")
    @Operation(summary = "导出项目 Markdown 压缩包")
    public jakarta.ws.rs.core.Response exportZip(@PathParam("itemId") Long itemId) {
        byte[] zip = exportService.itemZip(actor(), itemId);
        return jakarta.ws.rs.core.Response.ok(zip)
                .header("Content-Disposition", "attachment; filename*=UTF-8''"
                        + java.net.URLEncoder.encode("wiki-item-" + itemId + ".zip", java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    @GET
    @Path("/{itemId}/api-keys")
    @Operation(summary = "项目 API Key 列表")
    public R<List<WikiDtos.ApiKeyView>> apiKeys(@PathParam("itemId") Long itemId) {
        return R.ok(openApiService.list(actor(), itemId).stream()
                .map(k -> new WikiDtos.ApiKeyView(k.id, k.apiKey, k.apiSecret, k.remark,
                        k.createdByName, k.createdAt))
                .toList());
    }

    @POST
    @Path("/{itemId}/api-keys")
    @Log(title = "文档管理", businessType = 1)
    @Operation(summary = "生成 API Key")
    public R<WikiDtos.ApiKeyView> generateApiKey(@PathParam("itemId") Long itemId,
                                                 @Valid WikiDtos.ApiKeyCreateRequest request) {
        WikiApiKey key = openApiService.generate(actor(), itemId, request.remark());
        return R.ok(new WikiDtos.ApiKeyView(key.id, key.apiKey, key.apiSecret, key.remark,
                key.createdByName, key.createdAt));
    }

    @DELETE
    @Path("/{itemId}/api-keys/{keyId}")
    @Operation(summary = "删除 API Key")
    public R<Void> deleteApiKey(@PathParam("itemId") Long itemId,
                                @PathParam("keyId") Long keyId) {
        openApiService.delete(actor(), itemId, keyId);
        return R.ok();
    }

    private WikiPermissionService.Actor actor() {
        return permissionService.actor(jwt);
    }

    private int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
