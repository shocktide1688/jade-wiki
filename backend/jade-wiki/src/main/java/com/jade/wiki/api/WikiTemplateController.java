package com.jade.wiki.api;

import com.jade.common.api.R;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.service.WikiPermissionService;
import com.jade.wiki.service.WikiTemplateService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * 页面模板
 */
@Path("/api/v1/wiki/templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Wiki 模板")
public class WikiTemplateController {

    public record TemplateCreateRequest(@NotBlank String templateName, String templateContent) {}

    @Inject
    WikiTemplateService templateService;

    @Inject
    WikiPermissionService permissionService;

    @Inject
    JsonWebToken jwt;

    @GET
    @Operation(summary = "模板列表(内置 + 自建)")
    public R<List<WikiDtos.TemplateView>> list() {
        return R.ok(templateService.list().stream()
                .map(t -> new WikiDtos.TemplateView(t.id, t.templateName, t.templateContent,
                        t.isBuiltin, t.creatorName))
                .toList());
    }

    @POST
    @Operation(summary = "新建模板")
    public R<WikiDtos.TemplateView> create(@Valid TemplateCreateRequest request) {
        var t = templateService.create(permissionService.actor(jwt),
                request.templateName(), request.templateContent());
        return R.ok(new WikiDtos.TemplateView(t.id, t.templateName, t.templateContent,
                t.isBuiltin, t.creatorName));
    }

    @DELETE
    @Path("/{templateId}")
    @Operation(summary = "删除模板(内置不可删)")
    public R<Void> delete(@PathParam("templateId") Long templateId) {
        templateService.delete(permissionService.actor(jwt), templateId);
        return R.ok();
    }
}
