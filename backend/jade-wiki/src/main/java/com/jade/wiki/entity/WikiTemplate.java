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
 * 页面模板（新建页面时可选用）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_template")
public class WikiTemplate extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(name = "template_name", nullable = false, length = 128)
    public String templateName;

    @Column(name = "template_content", columnDefinition = "TEXT")
    public String templateContent;

    /** 1=系统内置（不可删） */
    @Column(name = "is_builtin", nullable = false)
    public Integer isBuiltin = 0;

    @Column(name = "creator_id")
    public Long creatorId;

    @Column(name = "creator_name", length = 64)
    public String creatorName;

    @Column(nullable = false)
    public Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    public static List<WikiTemplate> listActive() {
        return list("deleted = false", Sort.by("isBuiltin").descending().and("id").ascending());
    }
}
