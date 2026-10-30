package com.jade.wiki.service;

import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.wiki.entity.WikiApiKey;
import com.jade.wiki.entity.WikiCatalog;
import com.jade.wiki.entity.WikiItem;
import com.jade.wiki.entity.WikiPage;
import com.jade.wiki.entity.WikiPageHistory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

import static com.jade.wiki.service.WikiPermissionService.Actor;

@ApplicationScoped
public class WikiOpenApiService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    WikiPermissionService permission;

    // ============ Key 管理 ============

    public List<WikiApiKey> list(Actor actor, Long itemId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        return WikiApiKey.listByItem(itemId);
    }

    @Transactional
    public WikiApiKey generate(Actor actor, Long itemId, String remark) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        WikiApiKey key = new WikiApiKey();
        key.itemId = itemId;
        key.apiKey = "wk_" + randomHex(16);
        key.apiSecret = randomHex(32);
        key.remark = remark;
        key.createdBy = actor.userId();
        key.createdByName = actor.username();
        key.persist();
        return key;
    }

    @Transactional
    public void delete(Actor actor, Long itemId, Long keyId) {
        WikiItem item = permission.requireItem(itemId);
        permission.requireOwner(item, actor);
        WikiApiKey key = WikiApiKey.findById(keyId);
        if (key == null || key.deleted || !key.itemId.equals(itemId)) {
            throw new BizException(ResultCode.NOT_FOUND, "API Key 不存在");
        }
        key.deleted = true;
    }

    // ============ 开放写入（类 ShowDoc open api） ============

    public record OpenSaveResult(Long pageId, String pageTitle, String action) {}

    @Transactional
    public OpenSaveResult savePage(String apiKey, String apiSecret, String pageTitle,
                                   String pageContent, String catName) {
        WikiApiKey key = WikiApiKey.find("apiKey = ?1 and deleted = false", apiKey).firstResult();
        if (key == null || !MessageDigest.isEqual(
                key.apiSecret.getBytes(), (apiSecret == null ? "" : apiSecret).getBytes())) {
            throw new BizException(ResultCode.FORBIDDEN, "api_key 或 api_secret 无效");
        }
        WikiItem item = permission.requireItem(key.itemId);

        WikiCatalog catalog = null;
        if (catName != null && !catName.isBlank()) {
            catalog = WikiCatalog.listByItem(item.id).stream()
                    .filter(c -> c.catName.equals(catName.trim()))
                    .findFirst()
                    .orElseGet(() -> {
                        WikiCatalog c = new WikiCatalog();
                        c.itemId = item.id;
                        c.parentId = 0L;
                        c.catName = catName.trim();
                        c.persist();
                        return c;
                    });
        }

        WikiPage page = WikiPage.find("itemId = ?1 and pageTitle = ?2 and deleted = false",
                        item.id, pageTitle.trim()).firstResult();
        String action;
        if (page == null) {
            page = new WikiPage();
            page.itemId = item.id;
            page.catId = catalog == null ? null : catalog.id;
            page.pageTitle = pageTitle.trim();
            page.pageContent = pageContent;
            page.authorName = "open-api";
            page.persist();
            action = "created";
        } else {
            page.catId = catalog == null ? page.catId : catalog.id;
            page.pageContent = pageContent;
            page.authorName = "open-api";
            page.persistAndFlush();
            action = "updated";
        }

        WikiPageHistory history = new WikiPageHistory();
        history.pageId = page.id;
        history.pageTitle = page.pageTitle;
        history.pageContent = page.pageContent;
        history.authorName = "open-api";
        history.persist();
        WikiPageHistory.trim(page.id);

        return new OpenSaveResult(page.id, page.pageTitle, action);
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(buf)).substring(0, bytes * 2);
        } catch (NoSuchAlgorithmException e) {
            return HexFormat.of().formatHex(buf);
        }
    }
}
