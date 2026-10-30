package com.jade.wiki.api;

import com.jade.admin.aspect.Log;
import com.jade.common.api.R;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.service.WikiExportService;
import com.jade.wiki.service.WikiPageService;
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
 * Wiki 页面: CRUD / 回收站 / 历史版本 / 搜索
 *
 * 注意: 类级 @Path 首段必须与其他控制器(items/catalogs)互不重叠,
 * RESTEasy Reactive 的类级路径前缀节点会抢占同前缀的其他类路由。
 */
@Path("/api/v1/wiki/pages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Wiki 页面")
public class WikiPageController {

    @Inject
    WikiPageService pageService;

    @Inject
    WikiPermissionService permissionService;

    @Inject
    WikiExportService exportService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(summary = "页面列表/搜索/回收站 (keyword 或 trash 可选)")
    public R<List<WikiDtos.PageSummary>> list(@QueryParam("itemId") Long itemId,
                                              @QueryParam("catId") Long catId,
                                              @QueryParam("keyword") String keyword,
                                              @QueryParam("trash") Boolean trash) {
        WikiPermissionService.Actor actor = actor();
        if (Boolean.TRUE.equals(trash)) {
            return R.ok(pageService.trash(actor, itemId));
        }
        if (keyword != null && !keyword.isBlank()) {
            return R.ok(pageService.search(actor, itemId, keyword));
        }
        return R.ok(pageService.list(actor, itemId, catId));
    }

    @POST
    @Log(title = "文档管理", businessType = 1)
    @Operation(summary = "新建页面")
    public R<WikiDtos.PageDetail> create(@Valid WikiDtos.PageSaveRequest request) {
        return R.ok(pageService.create(actor(), request.itemId(), request));
    }

    @GET
    @Path("/{pageId}")
    @Operation(summary = "页面详情(含正文)")
    public R<WikiDtos.PageDetail> get(@PathParam("pageId") Long pageId) {
        return R.ok(pageService.get(actor(), pageId));
    }

    @PUT
    @Path("/{pageId}")
    @Log(title = "文档管理", businessType = 2)
    @Operation(summary = "保存页面(乐观锁, 冲突返回 4009)")
    public R<WikiDtos.PageDetail> update(@PathParam("pageId") Long pageId,
                                         @Valid WikiDtos.PageUpdateRequest request) {
        return R.ok(pageService.update(actor(), pageId, request));
    }

    @DELETE
    @Path("/{pageId}")
    @Log(title = "文档管理", businessType = 3)
    @Operation(summary = "删除页面(移入回收站)")
    public R<Void> delete(@PathParam("pageId") Long pageId) {
        pageService.delete(actor(), pageId);
        return R.ok();
    }

    @GET
    @Path("/{pageId}/export")
    @Operation(summary = "导出单页 Markdown")
    public jakarta.ws.rs.core.Response exportPage(@PathParam("pageId") Long pageId) {
        byte[] md = exportService.pageMarkdown(actor(), pageId);
        if (md == null) {
            return jakarta.ws.rs.core.Response.status(404).build();
        }
        return jakarta.ws.rs.core.Response.ok(md)
                .header("Content-Disposition", "attachment; filename*=UTF-8''"
                        + java.net.URLEncoder.encode("page-" + pageId + ".md", java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    @POST
    @Path("/{pageId}/restore")
    @Log(title = "文档管理", businessType = 2)
    @Operation(summary = "从回收站恢复页面")
    public R<Void> restore(@PathParam("pageId") Long pageId) {
        pageService.restore(actor(), pageId);
        return R.ok();
    }

    @POST
    @Path("/{pageId}/copy")
    @Log(title = "文档管理", businessType = 1)
    @Operation(summary = "复制页面")
    public R<WikiDtos.PageDetail> copy(@PathParam("pageId") Long pageId) {
        return R.ok(pageService.copy(actor(), pageId));
    }

    @PUT
    @Path("/{pageId}/move")
    @Operation(summary = "页面上移/下移 (direction=up|down)")
    public R<Void> move(@PathParam("pageId") Long pageId,
                        @Valid WikiDtos.MoveRequest request) {
        pageService.move(actor(), pageId, request.direction());
        return R.ok();
    }

    @GET
    @Path("/global-search")
    @Operation(summary = "全局搜索(我的项目范围, 跨项目)")
    public R<List<WikiDtos.GlobalSearchView>> globalSearch(@QueryParam("keyword") String keyword) {
        return R.ok(pageService.globalSearch(actor(), keyword));
    }

    @GET
    @Path("/{pageId}/history")
    @Operation(summary = "页面历史版本列表")
    public R<List<WikiDtos.HistoryView>> history(@PathParam("pageId") Long pageId) {
        return R.ok(pageService.history(actor(), pageId));
    }

    @GET
    @Path("/history-detail/{historyId}")
    @Operation(summary = "历史版本详情")
    public R<WikiDtos.HistoryDetail> historyDetail(@PathParam("historyId") Long historyId) {
        return R.ok(pageService.historyDetail(actor(), historyId));
    }

    private WikiPermissionService.Actor actor() {
        return permissionService.actor(jwt);
    }
}
