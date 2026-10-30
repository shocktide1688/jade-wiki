package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.entity.WikiCatalog;
import com.jade.wiki.entity.WikiItem;
import com.jade.wiki.entity.WikiPage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jade.wiki.service.WikiPermissionService.Actor;

@ApplicationScoped
public class WikiCatalogService {

    @Inject
    WikiPermissionService permission;

    @Inject
    WikiOperLogRecorder operLog;

    public List<WikiDtos.CatalogView> tree(Actor actor, Long itemId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireReadable(item, actor);
        return buildTree(itemId);
    }

    /** 无权限检查的目录树构建（匿名浏览接口复用, 权限由调用方控制） */
    public List<WikiDtos.CatalogView> buildTree(Long itemId) {
        List<WikiCatalog> all = WikiCatalog.listByItem(itemId);
        Map<Long, List<WikiCatalog>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.parentId));
        return buildChildren(byParent, 0L);
    }

    @Transactional
    public WikiDtos.CatalogView create(Actor actor, Long itemId, WikiDtos.CatalogCreateRequest req) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireEditable(item, actor);
        if (req.parentId() != null && req.parentId() > 0) {
            WikiCatalog parent = WikiCatalog.findById(req.parentId());
            if (parent == null || !parent.itemId.equals(itemId)) {
                throw new BizException(ResultCode.NOT_FOUND, "父目录不存在");
            }
        }
        WikiCatalog catalog = new WikiCatalog();
        catalog.itemId = itemId;
        catalog.parentId = req.parentId() == null ? 0L : req.parentId();
        catalog.catName = req.catName();
        catalog.sNumber = req.sNumber() == null ? 0 : req.sNumber();
        catalog.persist();
        operLog.record(actor, "文档管理", 1, "WikiCatalogService.create[item=" + itemId + ",cat=" + catalog.id + "]");
        return new WikiDtos.CatalogView(catalog.id, catalog.itemId, catalog.parentId,
                catalog.catName, catalog.sNumber, List.of());
    }

    @Transactional
    public void rename(Actor actor, Long catalogId, WikiDtos.CatalogRequest req) {
        WikiCatalog catalog = requireCatalog(catalogId);
        WikiItem item = permission.requireItem(catalog.itemId);
        permission.requireEditable(item, actor);
        catalog.catName = req.catName();
        if (req.sNumber() != null) {
            catalog.sNumber = req.sNumber();
        }
        operLog.record(actor, "文档管理", 2, "WikiCatalogService.rename[cat=" + catalogId + "]");
    }

    /** 软删目录, 目录下页面移入"未分类" */
    @Transactional
    public void delete(Actor actor, Long catalogId) {
        WikiCatalog catalog = requireCatalog(catalogId);
        WikiItem item = permission.requireItem(catalog.itemId);
        permission.requireEditable(item, actor);

        List<Long> subtree = collectSubtreeIds(catalog.itemId, catalogId);
        WikiCatalog.delete("id in ?1", subtree);
        List<WikiPage> pages = WikiPage.list("catId in ?1 and deleted = false", subtree);
        for (WikiPage page : pages) {
            page.catId = null;
        }
        operLog.record(actor, "文档管理", 3, "WikiCatalogService.delete[cat=" + catalogId + "]");
    }

    /** 目录上移/下移（同父级内交换排序号） */
    @Transactional
    public void move(Actor actor, Long catalogId, String direction) {
        WikiCatalog catalog = requireCatalog(catalogId);
        WikiItem item = permission.requireItem(catalog.itemId);
        permission.requireEditable(item, actor);
        List<WikiCatalog> siblings = WikiCatalog.list(
                "itemId = ?1 and parentId = ?2 and deleted = false order by sNumber, id",
                catalog.itemId, catalog.parentId);
        swapSort(siblings, catalogId, direction);
    }

    private void swapSort(List<WikiCatalog> ordered, Long targetId, String direction) {
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id.equals(targetId)) { idx = i; break; }
        }
        if (idx < 0) {
            throw new BizException(ResultCode.NOT_FOUND, "目录不在列表中");
        }
        int other = "up".equalsIgnoreCase(direction) ? idx - 1 : idx + 1;
        if (other < 0 || other >= ordered.size()) {
            return;
        }
        WikiCatalog self = ordered.get(idx);
        WikiCatalog neighbor = ordered.get(other);
        Integer tmp = self.sNumber;
        self.sNumber = neighbor.sNumber;
        neighbor.sNumber = tmp;
        if (self.sNumber.equals(neighbor.sNumber)) {
            if (self.id < neighbor.id) {
                self.sNumber = neighbor.sNumber + 1;
            } else {
                neighbor.sNumber = self.sNumber + 1;
            }
        }
        self.persistAndFlush();
        neighbor.persistAndFlush();
    }

    private List<WikiDtos.CatalogView> buildChildren(Map<Long, List<WikiCatalog>> byParent, Long parentId) {
        List<WikiDtos.CatalogView> views = new ArrayList<>();
        for (WikiCatalog c : byParent.getOrDefault(parentId, List.of())) {
            views.add(new WikiDtos.CatalogView(c.id, c.itemId, c.parentId, c.catName,
                    c.sNumber, buildChildren(byParent, c.id)));
        }
        return views;
    }

    private List<Long> collectSubtreeIds(Long itemId, Long catalogId) {
        List<Long> ids = new ArrayList<>();
        ids.add(catalogId);
        List<WikiCatalog> all = WikiCatalog.listByItem(itemId);
        Map<Long, List<WikiCatalog>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.parentId));
        collect(byParent, catalogId, ids);
        return ids;
    }

    private void collect(Map<Long, List<WikiCatalog>> byParent, Long parentId, List<Long> ids) {
        for (WikiCatalog child : byParent.getOrDefault(parentId, List.of())) {
            ids.add(child.id);
            collect(byParent, child.id, ids);
        }
    }

    private WikiCatalog requireCatalog(Long catalogId) {
        WikiCatalog catalog = WikiCatalog.findById(catalogId);
        if (catalog == null || catalog.deleted) {
            throw new BizException(ResultCode.NOT_FOUND, "目录不存在");
        }
        return catalog;
    }
}
