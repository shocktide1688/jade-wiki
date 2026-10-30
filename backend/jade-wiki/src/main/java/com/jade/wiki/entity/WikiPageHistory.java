package com.jade.wiki.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 页面历史版本（每次保存快照一份, 默认保留最近 HISTORY_KEEP 份）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_page_history")
public class WikiPageHistory extends PanacheEntityBase {

    public static final int HISTORY_KEEP = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "page_id", nullable = false)
    public Long pageId;

    @Column(name = "page_title")
    public String pageTitle;

    @Column(name = "page_content", columnDefinition = "TEXT")
    public String pageContent;

    @Column(name = "author_id")
    public Long authorId;

    @Column(name = "author_name", length = 64)
    public String authorName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    public static List<WikiPageHistory> listByPage(Long pageId) {
        return list("pageId", Sort.by("id").descending(), pageId);
    }

    /** 只保留最近 HISTORY_KEEP 份 */
    public static void trim(Long pageId) {
        List<WikiPageHistory> all = list("pageId", Sort.by("id").descending(), pageId);
        if (all.size() > HISTORY_KEEP) {
            List<Long> stale = all.subList(HISTORY_KEEP, all.size()).stream().map(h -> h.id).toList();
            delete("id in ?1", stale);
        }
    }
}
