package com.jade.wiki.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wiki API DTO 集中定义（record 只读）
 */
public final class WikiDtos {

    private WikiDtos() {
    }

    // ============ 项目 ============

    public record ItemCreateRequest(
            @NotBlank @Size(max = 128) String itemName,
            @Size(max = 512) String itemDescription,
            String visibility,
            String password) {
    }

    public record ItemUpdateRequest(
            @NotBlank @Size(max = 128) String itemName,
            @Size(max = 512) String itemDescription,
            String visibility,
            String password) {
    }

    public record ItemView(
            Long id,
            String itemName,
            String itemDescription,
            String visibility,
            String itemType,
            Long creatorId,
            String creatorName,
            Boolean isArchive,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Integer myRole,
            Long memberCount) {
    }

    public record MemberAddRequest(
            @NotBlank String username,
            Integer memberGroup) {
    }

    public record MemberUpdateRequest(Integer memberGroup) {
    }

    public record MemberView(
            Long id,
            Long userId,
            String username,
            String nickname,
            Integer memberGroup) {
    }

    // ============ 目录 ============

    public record CatalogRequest(
            @NotBlank @Size(max = 128) String catName,
            Integer sNumber) {
    }

    public record CatalogCreateRequest(
            @NotNull Long itemId,
            @NotBlank @Size(max = 128) String catName,
            Long parentId,
            Integer sNumber) {
    }

    public record CatalogView(
            Long id,
            Long itemId,
            Long parentId,
            String catName,
            Integer sNumber,
            List<CatalogView> children) {
    }

    // ============ 页面 ============

    public record PageSaveRequest(
            @NotNull Long itemId,
            @NotBlank @Size(max = 255) String pageTitle,
            String pageContent,
            Long catId,
            Integer sNumber) {
    }

    public record PageUpdateRequest(
            @NotBlank @Size(max = 255) String pageTitle,
            String pageContent,
            Long catId,
            Integer sNumber,
            @NotNull Long version) {
    }

    public record PageSummary(
            Long id,
            Long itemId,
            Long catId,
            String pageTitle,
            Integer sNumber,
            String authorName,
            Long version,
            LocalDateTime updatedAt) {
    }

    public record PageDetail(
            Long id,
            Long itemId,
            Long catId,
            String pageTitle,
            String pageContent,
            Integer sNumber,
            String authorName,
            Long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record HistoryView(
            Long id,
            Long pageId,
            String pageTitle,
            String authorName,
            LocalDateTime createdAt) {
    }

    public record HistoryDetail(
            Long id,
            Long pageId,
            String pageTitle,
            String pageContent,
            String authorName,
            LocalDateTime createdAt) {
    }

    // ============ 模板 ============

    public record TemplateView(
            Long id,
            String templateName,
            String templateContent,
            Integer isBuiltin,
            String creatorName) {
    }

    // ============ 附件 ============

    public record AttachmentView(
            Long id,
            Long itemId,
            String originalName,
            String fileSuffix,
            Long fileSize,
            String contentType,
            String url) {
    }

    // ============ 分享/匿名浏览 ============

    public record ShareVerifyRequest(
            @NotNull Long itemId,
            String password) {
    }

    public record ShareVerifyResponse(
            String viewToken,
            String itemName,
            String visibility) {
    }

    public record ViewItem(
            Long id,
            String itemName,
            String itemDescription,
            String visibility,
            String creatorName,
            LocalDateTime updatedAt) {
    }

    // ============ 开放 API ============

    public record ApiKeyCreateRequest(String remark) {
    }

    public record ApiKeyView(
            Long id,
            String apiKey,
            String apiSecret,
            String remark,
            String createdByName,
            LocalDateTime createdAt) {
    }

    public record OpenApiPageSaveRequest(
            @NotBlank String apiKey,
            @NotBlank String apiSecret,
            @NotBlank String pageTitle,
            String pageContent,
            String catName) {
    }

    public record GlobalSearchView(
            Long itemId,
            String itemName,
            Long pageId,
            String pageTitle) {
    }

    public record MoveRequest(
            @NotBlank String direction) {
    }
}
