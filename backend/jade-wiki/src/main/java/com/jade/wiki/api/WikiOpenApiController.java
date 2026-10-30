package com.jade.wiki.api;

import com.jade.common.api.R;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.service.WikiOpenApiService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 开放 API（类 ShowDoc open api）: CI/脚本凭 api_key 推送文档, 无需登录
 */
@Path("/api/open/wiki")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@Tag(name = "Wiki 开放API")
public class WikiOpenApiController {

    @Inject
    WikiOpenApiService openApiService;

    @POST
    @Path("/page/save")
    @Operation(summary = "按标题保存页面(存在则覆盖, 不存在则创建; catName 可自动建目录)")
    public R<WikiOpenApiService.OpenSaveResult> savePage(@Valid WikiDtos.OpenApiPageSaveRequest request) {
        return R.ok(openApiService.savePage(request.apiKey(), request.apiSecret(),
                request.pageTitle(), request.pageContent(), request.catName()));
    }
}
