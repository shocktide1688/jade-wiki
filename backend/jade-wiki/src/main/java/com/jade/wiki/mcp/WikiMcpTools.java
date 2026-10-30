package com.jade.wiki.mcp;

import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.entity.WikiItem;
import com.jade.wiki.entity.WikiPage;
import com.jade.wiki.service.WikiCatalogService;
import com.jade.wiki.service.WikiItemService;
import com.jade.wiki.service.WikiPageService;
import com.jade.mcp.auth.McpTokenAuthMechanism;
import com.jade.wiki.service.WikiPermissionService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade Wiki MCP 工具集 (只读)
 *
 * AI 使用链: list_projects → get_project_tree / search_pages → read_page
 * 鉴权: McpAuthFilter 解析 X-Mcp-Token 后写入 McpUserContext, 所有工具按该用户权限过滤。
 */
@ApplicationScoped
public class WikiMcpTools {

    /** 单页正文上限, 超出截断 */
    private static final int MAX_CONTENT_CHARS = 40_000;

    @Inject
    WikiPermissionService permission;

    @Inject
    WikiItemService itemService;

    @Inject
    WikiCatalogService catalogService;

    @Inject
    WikiPageService pageService;

    @Inject
    Instance<SecurityIdentity> identity;

    public record ProjectBrief(long id, String name, String description, String visibility) {}

    @Tool(description = "列出当前令牌可见的文档项目, 返回项目ID/名称/描述/可见性")
    public List<ProjectBrief> listProjects(
            @ToolArg(description = "按项目名称过滤, 可空", required = false) String keyword) {
        try {
            var actor = actor();
            return itemService.page(actor, keyword, 1, 50).getRecords().stream()
                    .map(i -> new ProjectBrief(i.id(), i.itemName(),
                            i.itemDescription() == null ? "" : i.itemDescription(), i.visibility()))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("查询失败: " + rootMessage(e));
        }
    }

    public record TreeEntry(String type, long id, long depth, String title) {}

    @Tool(description = "读取项目的目录树与页面清单 (type=catalog目录/page页面, depth为层级)")
    public List<TreeEntry> getProjectTree(
            @ToolArg(description = "项目ID, 可用 list_projects 获取") long itemId) {
        try {
        var actor = actor();
        WikiItem item = permission.requireItem(itemId);
        permission.requireReadable(item, actor);

        List<TreeEntry> result = new ArrayList<>();
        walkCatalog(actor, itemId, catalogService.tree(actor, itemId), 0, result);
        // 未分类页面
        for (WikiDtos.PageSummary p : pageService.list(actor, itemId, null)) {
            result.add(new TreeEntry("page", p.id(), 0, p.pageTitle()));
        }
        return result;
        } catch (Exception e) {
            throw new RuntimeException("读取目录失败: " + rootMessage(e));
        }
    }

    public record PageHit(long pageId, long itemId, String itemName, String pageTitle) {}

    @Tool(description = "全量搜索可见项目内的页面 (标题+正文), 返回命中页ID与标题")
    public List<PageHit> searchPages(
            @ToolArg(description = "搜索关键词, 空则列出页面") String keyword,
            @ToolArg(description = "限定项目ID, 可空=搜全部项目", required = false) Long itemId,
            @ToolArg(description = "最大返回条数, 默认 10", required = false) Integer limit) {
        try {
        var actor = actor();
        int cap = limit == null || limit <= 0 ? 10 : Math.min(limit, 30);
        List<PageHit> hits = new ArrayList<>();
        if (itemId != null) {
            collectHits(actor, itemId, "", keyword, hits, cap);
            return hits;
        }
        for (WikiDtos.ItemView item : itemService.page(actor, null, 1, 50).getRecords()) {
            collectHits(actor, item.id(), item.itemName(), keyword, hits, cap);
            if (hits.size() >= cap) break;
        }
        return hits;
        } catch (Exception e) {
            throw new RuntimeException("搜索失败: " + rootMessage(e));
        }
    }

    @Tool(description = "读取页面完整 Markdown 内容 (含标题, 超长截断)")
    public String readPage(
            @ToolArg(description = "页面ID, 可用 search_pages/get_project_tree 获取") long pageId) {
        try {
        var actor = actor();
        WikiDtos.PageDetail page = pageService.get(actor, pageId);
        String content = page.pageContent() == null ? "" : page.pageContent();
        if (content.length() > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS) + "\n\n... (内容过长已截断, 原文 "
                    + content.length() + " 字符)";
        }
        // 不重复拼标题: 页面正文通常自带 H1 (搜索/树里已提供标题上下文)
        return content;
        } catch (Exception e) {
            // 业务错误以文本结果返回 (isError 语义), AI 可直接理解并调整调用
            return "错误: " + rootMessage(e);
        }
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    // ---- 内部 ----

    /** MCP 身份: McpTokenAuthMechanism 认证后写入 SecurityIdentity (attribute userId) */
    private WikiPermissionService.Actor actor() {
        SecurityIdentity id = identity.get();
        Long userId = (Long) id.getAttribute(McpTokenAuthMechanism.ATTR_USER_ID);
        if (userId == null) {
            throw new IllegalStateException("MCP 请求缺少身份 (未通过 PAT 认证)");
        }
        return new WikiPermissionService.Actor(userId, id.getPrincipal().getName(), false);
    }

    private void walkCatalog(WikiPermissionService.Actor actor, Long itemId,
                             List<WikiDtos.CatalogView> children, int depth, List<TreeEntry> out) {
        for (WikiDtos.CatalogView c : children) {
            out.add(new TreeEntry("catalog", c.id(), depth, c.catName()));
            walkCatalog(actor, itemId, c.children(), depth + 1, out);
            for (WikiDtos.PageSummary p : pageService.list(actor, itemId, c.id())) {
                out.add(new TreeEntry("page", p.id(), depth + 1, p.pageTitle()));
            }
        }
    }

    private void collectHits(WikiPermissionService.Actor actor, Long itemId, String itemName,
                             String keyword, List<PageHit> hits, int cap) {
        if (hits.size() >= cap) return;
        try {
            String kw = keyword == null ? "" : keyword.trim();
            if (kw.isEmpty()) {
                for (WikiDtos.PageSummary p : pageService.list(actor, itemId, null)) {
                    if (hits.size() >= cap) return;
                    hits.add(new PageHit(p.id(), itemId, itemName, p.pageTitle()));
                }
                return;
            }
            List<WikiPage> pages = WikiPage.list(
                    "itemId = ?1 and deleted = false and (lower(pageTitle) like ?2 or lower(pageContent) like ?2)",
                    itemId, "%" + kw.toLowerCase() + "%");
            for (WikiPage p : pages) {
                if (hits.size() >= cap) return;
                hits.add(new PageHit(p.id, itemId, itemName, p.pageTitle));
            }
        } catch (Exception ignored) {
            // 单个项目无权限/不存在时跳过, 不中断全局搜索
        }
    }
}
