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
 * 项目内目录树节点
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_catalog")
public class WikiCatalog extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "item_id", nullable = false)
    public Long itemId;

    /** 父目录 id, 0 = 顶级 */
    @Column(name = "parent_id", nullable = false)
    public Long parentId = 0L;

    @Column(name = "cat_name", nullable = false, length = 128)
    public String catName;

    @Column(name = "s_number", nullable = false)
    public Integer sNumber = 0;

    @Column(nullable = false)
    public Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    public static List<WikiCatalog> listByItem(Long itemId) {
        return list("itemId = ?1 and deleted = false",
                Sort.by("sNumber").ascending().and("id").ascending(), itemId);
    }
}
