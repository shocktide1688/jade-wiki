package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.wiki.api.dto.WikiDtos;
import com.jade.wiki.entity.WikiItem;
import com.jade.wiki.entity.WikiPage;
import com.jade.wiki.entity.WikiPageHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.jade.wiki.service.WikiPermissionService.Actor;

@ApplicationScoped
public class WikiPageService {

    @Inject
    WikiPermissionService permission;

    @Inject
    WikiOperLogRecorder operLog;

    public List<WikiDtos.PageSummary> list(Actor actor, Long itemId, Long catId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireReadable(item, actor);
        return listOf(itemId, catId);
    }

    /** 无权限检查的列表（匿名浏览接口复用, 权限由调用方控制） */
    public List<WikiDtos.PageSummary> listOf(Long itemId, Long catId) {
        return WikiPage.listByItem(itemId, catId).stream().map(this::toSummary).toList();
    }

    /** 无权限检查的详情（匿名浏览接口复用, 权限由调用方控制） */
    public WikiDtos.PageDetail detailOf(WikiPage page) {
        return toDetail(page);
    }

    public WikiDtos.PageDetail get(Actor actor, Long pageId) {
        WikiPage page = requirePage(pageId);
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireReadable(item, actor);
        return toDetail(page);
    }

    @Transactional
    public WikiDtos.PageDetail create(Actor actor, Long itemId, WikiDtos.PageSaveRequest req) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireEditable(item, actor);
        WikiPage page = new WikiPage();
        page.itemId = itemId;
        page.catId = req.catId();
        page.pageTitle = req.pageTitle();
        page.pageContent = req.pageContent();
        page.sNumber = req.sNumber() == null ? 0 : req.sNumber();
        page.authorId = actor.userId();
        page.authorName = actor.username();
        page.persist();
        operLog.record(actor, "文档管理", 1, "WikiPageService.create[item=" + itemId + ",page=" + page.id + "]");
        return toDetail(page);
    }

    @Transactional
    public WikiDtos.PageDetail update(Actor actor, Long pageId, WikiDtos.PageUpdateRequest req) {
        WikiPage page = requirePage(pageId);
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireEditable(item, actor);

        if (!req.version().equals(page.version)) {
            throw new BizException(ResultCode.CONFLICT,
                    "页面已被他人修改(当前版本 v" + page.version + "), 请刷新后重试");
        }

        page.catId = req.catId();
        page.pageTitle = req.pageTitle();
        page.pageContent = req.pageContent();
        page.sNumber = req.sNumber() == null ? page.sNumber : req.sNumber();
        page.authorId = actor.userId();
        page.authorName = actor.username();

        // 立即 flush 让 @Version 自增, 返回给前端最新版本号
        page.persistAndFlush();

        WikiPageHistory history = new WikiPageHistory();
        history.pageId = page.id;
        history.pageTitle = page.pageTitle;
        history.pageContent = page.pageContent;
        history.authorId = actor.userId();
        history.authorName = actor.username();
        history.persist();
        WikiPageHistory.trim(page.id);
        operLog.record(actor, "文档管理", 2, "WikiPageService.update[page=" + pageId + ",v=" + page.version + "]");

        return toDetail(page);
    }

    @Transactional
    public void delete(Actor actor, Long pageId) {
        WikiPage page = requirePage(pageId);
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireEditable(item, actor);
        page.deleted = true;
        operLog.record(actor, "文档管理", 3, "WikiPageService.delete[page=" + pageId + "]");
    }

    public List<WikiDtos.PageSummary> trash(Actor actor, Long itemId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireEditable(item, actor);
        return WikiPage.listTrash(itemId).stream().map(this::toSummary).toList();
    }

    @Transactional
    public void restore(Actor actor, Long pageId) {
        WikiPage page = WikiPage.findById(pageId);
        if (page == null || !page.deleted) {
            throw new BizException(ResultCode.NOT_FOUND, "回收站中不存在该页面");
        }
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireEditable(item, actor);
        page.deleted = false;
        operLog.record(actor, "文档管理", 2, "WikiPageService.restore[page=" + pageId + "]");
    }

    public List<WikiDtos.HistoryView> history(Actor actor, Long pageId) {
        WikiPage page = requirePage(pageId);
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireReadable(item, actor);
        return WikiPageHistory.listByPage(pageId).stream()
                .map(h -> new WikiDtos.HistoryView(h.id, h.pageId, h.pageTitle, h.authorName, h.createdAt))
                .toList();
    }

    public WikiDtos.HistoryDetail historyDetail(Actor actor, Long historyId) {
        WikiPageHistory history = WikiPageHistory.findById(historyId);
        if (history == null) {
            throw new BizException(ResultCode.NOT_FOUND, "历史版本不存在");
        }
        WikiPage page = requirePage(history.pageId);
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireReadable(item, actor);
        return new WikiDtos.HistoryDetail(history.id, history.pageId, history.pageTitle,
                history.pageContent, history.authorName, history.createdAt);
    }

    public List<WikiDtos.PageSummary> search(Actor actor, Long itemId, String keyword) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireReadable(item, actor);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            return List.of();
        }
        List<WikiPage> pages = WikiPage.list(
                "itemId = ?1 and deleted = false and (lower(pageTitle) like ?2 or lower(pageContent) like ?2)",
                itemId, "%" + kw + "%");
        return pages.stream().map(this::toSummary).toList();
    }

    /** 复制页面（含正文, 不带历史） */
    @Transactional
    public WikiDtos.PageDetail copy(Actor actor, Long pageId) {
        WikiPage source = requirePage(pageId);
        WikiItem item = permission.requireItem(source.itemId);
        permission.requireEditable(item, actor);
        WikiPage copy = new WikiPage();
        copy.itemId = source.itemId;
        copy.catId = source.catId;
        copy.pageTitle = source.pageTitle + " - 副本";
        copy.pageContent = source.pageContent;
        copy.sNumber = nextSNumber(source.itemId, source.catId);
        copy.authorId = actor.userId();
        copy.authorName = actor.username();
        copy.persist();
        operLog.record(actor, "文档管理", 1, "WikiPageService.copy[from=" + pageId + ",to=" + copy.id + "]");
        return toDetail(copy);
    }

    /** 页面上移/下移（同级同目录内交换排序号） */
    @Transactional
    public void move(Actor actor, Long pageId, String direction) {
        WikiPage page = requirePage(pageId);
        WikiItem item = permission.requireItem(page.itemId);
        permission.requireEditable(item, actor);
        List<WikiPage> siblings = page.catId == null
                ? WikiPage.list("itemId = ?1 and catId is null and deleted = false order by sNumber, id", page.itemId)
                : WikiPage.list("itemId = ?1 and catId = ?2 and deleted = false order by sNumber, id",
                        page.itemId, page.catId);
        swapSort(siblings, page.id, direction);
    }

    /** 全局搜索: 在"我的项目"范围内跨项目搜页面 */
    public List<WikiDtos.GlobalSearchView> globalSearch(Actor actor, String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty()) {
            return List.of();
        }
        List<WikiDtos.GlobalSearchView> result = new ArrayList<>();
        List<com.jade.wiki.entity.WikiItem> items =
                com.jade.wiki.entity.WikiItem.findMine(actor.userId(), null).page(
                        io.quarkus.panache.common.Page.of(0, 100)).list();
        for (com.jade.wiki.entity.WikiItem item : items) {
            List<WikiPage> hits = WikiPage.list(
                    "itemId = ?1 and deleted = false and lower(pageTitle) like ?2 order by sNumber, id",
                    item.id, "%" + kw + "%");
            if (hits.isEmpty()) {
                hits = WikiPage.list(
                        "itemId = ?1 and deleted = false and lower(pageContent) like ?2",
                        item.id, "%" + kw + "%");
                if (hits.size() > 3) hits = hits.subList(0, 3);
            } else if (hits.size() > 3) {
                hits = hits.subList(0, 3);
            }
            for (WikiPage hit : hits) {
                result.add(new WikiDtos.GlobalSearchView(item.id, item.itemName, hit.id, hit.pageTitle));
            }
            if (result.size() >= 30) break;
        }
        return result.subList(0, Math.min(result.size(), 30));
    }

    private Integer nextSNumber(Long itemId, Long catId) {
        // 注意: 不用 "is not distinct from" —— SQLite 方言会翻译成 MySQL 的 <=> 导致语法错误
        WikiPage last = (catId == null
                ? WikiPage.find("itemId = ?1 and catId is null and deleted = false order by sNumber desc, id desc", itemId)
                : WikiPage.find("itemId = ?1 and catId = ?2 and deleted = false order by sNumber desc, id desc", itemId, catId))
                .firstResult();
        return last == null ? 0 : last.sNumber + 1;
    }

    /** 在有序列表中把 targetId 与相邻元素交换 sNumber, 实现"上移/下移" */
    public static void swapSort(List<? extends com.jade.wiki.entity.WikiPage> ordered, Long targetId, String direction) {
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id.equals(targetId)) { idx = i; break; }
        }
        if (idx < 0) {
            throw new BizException(ResultCode.NOT_FOUND, "页面不在列表中");
        }
        int other = "up".equalsIgnoreCase(direction) ? idx - 1 : idx + 1;
        if (other < 0 || other >= ordered.size()) {
            return; // 已在边界, 无需移动
        }
        WikiPage self = ordered.get(idx);
        WikiPage neighbor = ordered.get(other);
        Integer tmp = self.sNumber;
        self.sNumber = neighbor.sNumber;
        neighbor.sNumber = tmp;
        if (self.sNumber.equals(neighbor.sNumber)) {
            // 排序号相同(历史数据)时强制区分, id 小的排前面
            if (self.id < neighbor.id) {
                self.sNumber = neighbor.sNumber + 1;
            } else {
                neighbor.sNumber = self.sNumber + 1;
            }
        }
        self.persistAndFlush();
        neighbor.persistAndFlush();
    }

    private WikiPage requirePage(Long pageId) {
        WikiPage page = WikiPage.findById(pageId);
        if (page == null || page.deleted) {
            throw new BizException(ResultCode.NOT_FOUND, "页面不存在");
        }
        return page;
    }

    private WikiDtos.PageSummary toSummary(WikiPage page) {
        return new WikiDtos.PageSummary(page.id, page.itemId, page.catId, page.pageTitle,
                page.sNumber, page.authorName, page.version, page.updatedAt);
    }

    private WikiDtos.PageDetail toDetail(WikiPage page) {
        return new WikiDtos.PageDetail(page.id, page.itemId, page.catId, page.pageTitle,
                page.pageContent, page.sNumber, page.authorName, page.version,
                page.createdAt, page.updatedAt);
    }
}
