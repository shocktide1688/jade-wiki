package com.jade.wiki.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Wiki 页面（Markdown 正文, version 乐观锁防并发编辑覆盖）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_page")
public class WikiPage extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "item_id", nullable = false)
    public Long itemId;

    /** 目录 id, NULL = 未分类 */
    @Column(name = "cat_id")
    public Long catId;

    @Column(name = "page_title", nullable = false)
    public String pageTitle;

    @Column(name = "page_content", columnDefinition = "TEXT")
    public String pageContent;

    @Column(name = "s_number", nullable = false)
    public Integer sNumber = 0;

    @Column(name = "author_id")
    public Long authorId;

    @Column(name = "author_name", length = 64)
    public String authorName;

    /** 乐观锁, PUT 必须携带客户端持有的版本号 */
    @Version
    @Column(nullable = false)
    public Long version = 0L;

    @Column(nullable = false)
    public Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public static List<WikiPage> listByItem(Long itemId, Long catId) {
        if (catId == null) {
            return list("itemId = ?1 and deleted = false",
                    Sort.by("sNumber").ascending().and("id").ascending(), itemId);
        }
        return list("itemId = ?1 and catId = ?2 and deleted = false",
                Sort.by("sNumber").ascending().and("id").ascending(), itemId, catId);
    }

    public static List<WikiPage> listTrash(Long itemId) {
        return list("itemId = ?1 and deleted = true",
                Sort.by("updatedAt").descending(), itemId);
    }
}
