package com.jade.wiki.api;

import com.jade.common.api.R;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.entity.WikiPage;
import com.jade.wiki.service.WikiCatalogService;
import com.jade.wiki.service.WikiPageService;
import com.jade.wiki.service.WikiPermissionService;
import com.jade.wiki.service.WikiShareService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * 匿名浏览（ShowDoc 式分享）: 公开项目免登录只读, 私密项目凭密码换 token 访问
 */
@Path("/api/v1/wiki")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@Tag(name = "Wiki 分享浏览")
public class WikiViewController {

    @Inject
    WikiPermissionService permissionService;

    @Inject
    WikiShareService shareService;

    @Inject
    WikiCatalogService catalogService;

    @Inject
    WikiPageService pageService;

    /** 凭密码换取访问 token（公开项目直接返回） */
    @POST
    @Path("/share/verify")
    @Operation(summary = "分享密码校验, 返回 viewToken")
    public R<WikiDtos.ShareVerifyResponse> verify(@Valid WikiDtos.ShareVerifyRequest request) {
        var item = permissionService.requireItem(request.itemId());
        String token = shareService.verify(item, request.password());
        return R.ok(new WikiDtos.ShareVerifyResponse(token, item.itemName, item.visibility));
    }

    @GET
    @Path("/view/items/{itemId}")
    @Operation(summary = "匿名读取项目信息")
    public R<WikiDtos.ViewItem> item(@PathParam("itemId") Long itemId,
                                     @HeaderParam(WikiShareService.TOKEN_HEADER) String token) {
        var item = permissionService.requireItem(itemId);
        shareService.requireViewable(item, token);
        return R.ok(new WikiDtos.ViewItem(item.id, item.itemName, item.itemDescription,
                item.visibility, item.creatorName, item.updatedAt));
    }

    @GET
    @Path("/view/catalogs")
    @Operation(summary = "匿名读取目录树")
    public R<List<WikiDtos.CatalogView>> catalogs(@QueryParam("itemId") Long itemId,
                                                  @HeaderParam(WikiShareService.TOKEN_HEADER) String token) {
        var item = permissionService.requireItem(itemId);
        shareService.requireViewable(item, token);
        return R.ok(catalogService.buildTree(itemId));
    }

    @GET
    @Path("/view/pages")
    @Operation(summary = "匿名读取页面列表")
    public R<List<WikiDtos.PageSummary>> pages(@QueryParam("itemId") Long itemId,
                                               @QueryParam("catId") Long catId,
                                               @HeaderParam(WikiShareService.TOKEN_HEADER) String token) {
        var item = permissionService.requireItem(itemId);
        shareService.requireViewable(item, token);
        return R.ok(pageService.listOf(itemId, catId));
    }

    @GET
    @Path("/view/pages/{pageId}")
    @Operation(summary = "匿名读取页面详情")
    public R<WikiDtos.PageDetail> page(@PathParam("pageId") Long pageId,
                                       @HeaderParam(WikiShareService.TOKEN_HEADER) String token) {
        WikiPage page = WikiPage.findById(pageId);
        if (page == null || page.deleted) {
            return R.fail(4004, "页面不存在");
        }
        var item = permissionService.requireItem(page.itemId);
        shareService.requireViewable(item, token);
        return R.ok(pageService.detailOf(page));
    }
}
