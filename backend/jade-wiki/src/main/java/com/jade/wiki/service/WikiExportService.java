package com.jade.wiki.service;

import com.jade.wiki.entity.WikiCatalog;
import com.jade.wiki.entity.WikiItem;
import com.jade.wiki.entity.WikiPage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.jade.wiki.service.WikiPermissionService.Actor;

@ApplicationScoped
public class WikiExportService {

    @Inject
    WikiPermissionService permission;

    /** 单页 markdown 字节 */
    public byte[] pageMarkdown(Actor actor, Long pageId) {
        WikiPage page = WikiPage.findById(pageId);
        if (page == null || page.deleted) {
            return null;
        }
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireReadable(item, actor);
        return markdownOf(page).getBytes(StandardCharsets.UTF_8);
    }

    /** 项目全量 markdown 打包 zip（目录作为子文件夹） */
    public byte[] itemZip(Actor actor, Long itemId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireReadable(item, actor);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bos);

        try {
            List<WikiPage> allPages = WikiPage.list(
                    "itemId = ?1 and deleted = false", itemId);
            List<WikiCatalog> catalogs = WikiCatalog.listByItem(itemId);

            // README 索引
            StringBuilder index = new StringBuilder();
            index.append("# ").append(item.itemName).append("\n\n");
            if (item.itemDescription != null && !item.itemDescription.isBlank()) {
                index.append(item.itemDescription).append("\n\n");
            }
            index.append("> 导出自 Jade Wiki · 页面数 ").append(allPages.size()).append("\n\n");
            for (WikiPage p : allPages) {
                index.append("- ").append(p.pageTitle).append("\n");
            }
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write(index.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // 未分类页面
            for (WikiPage p : allPages) {
                if (p.catId == null) {
                    writePage(zip, p, null);
                }
            }
            // 按目录层级写页面
            writeCatalogZip(zip, catalogs, allPages, null, "");
            zip.finish();
        } catch (Exception e) {
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    private void writeCatalogZip(ZipOutputStream zip, List<WikiCatalog> catalogs,
                                 List<WikiPage> pages, Long parentId, String prefix) throws Exception {
        for (WikiCatalog c : catalogs) {
            if (!c.parentId.equals(parentId == null ? 0L : parentId)) {
                continue;
            }
            String sub = prefix.isEmpty() ? safeName(c.catName) : prefix + "/" + safeName(c.catName);
            for (WikiPage p : pages) {
                if (c.id.equals(p.catId)) {
                    writePage(zip, p, sub);
                }
            }
            writeCatalogZip(zip, catalogs, pages, c.id, sub);
        }
    }

    private void writePage(ZipOutputStream zip, WikiPage page, String folder) throws Exception {
        String name = safeName(page.pageTitle) + ".md";
        String path = (folder == null || folder.isEmpty()) ? name : folder + "/" + name;
        zip.putNextEntry(new ZipEntry(path));
        zip.write(markdownOf(page).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String markdownOf(WikiPage page) {
        return "# " + page.pageTitle + "\n\n"
                + (page.pageContent == null ? "" : page.pageContent)
                + "\n";
    }

    private String safeName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "untitled" : cleaned;
    }
}
