package com.jade.wiki.api;

import com.jade.admin.aspect.Log;
import com.jade.common.api.R;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.service.WikiCatalogService;
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
 * Wiki 目录树管理 (类级路径首段 catalogs, 与 items/pages 控制器互不重叠)
 */
@Path("/api/v1/wiki/catalogs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Wiki 目录")
public class WikiCatalogController {

    @Inject
    WikiCatalogService catalogService;

    @Inject
    WikiPermissionService permissionService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(summary = "项目目录树")
    public R<List<WikiDtos.CatalogView>> tree(@QueryParam("itemId") Long itemId) {
        return R.ok(catalogService.tree(actor(), itemId));
    }

    @POST
    @Log(title = "文档管理", businessType = 1)
    @Operation(summary = "新建目录")
    public R<WikiDtos.CatalogView> create(@Valid WikiDtos.CatalogCreateRequest request) {
        return R.ok(catalogService.create(actor(), request.itemId(), request));
    }

    @PUT
    @Path("/{catalogId}")
    @Log(title = "文档管理", businessType = 2)
    @Operation(summary = "重命名/排序目录")
    public R<Void> rename(@PathParam("catalogId") Long catalogId,
                          @Valid WikiDtos.CatalogRequest request) {
        catalogService.rename(actor(), catalogId, request);
        return R.ok();
    }

    @PUT
    @Path("/{catalogId}/move")
    @Operation(summary = "目录上移/下移 (direction=up|down)")
    public R<Void> move(@PathParam("catalogId") Long catalogId,
                        @Valid WikiDtos.MoveRequest request) {
        catalogService.move(actor(), catalogId, request.direction());
        return R.ok();
    }

    @DELETE
    @Path("/{catalogId}")
    @Log(title = "文档管理", businessType = 3)
    @Operation(summary = "删除目录(页面移入未分类)")
    public R<Void> delete(@PathParam("catalogId") Long catalogId) {
        catalogService.delete(actor(), catalogId);
        return R.ok();
    }

    private WikiPermissionService.Actor actor() {
        return permissionService.actor(jwt);
    }
}
