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
 * 开放 API 凭据（CI 从代码注释推送文档, 类 ShowDoc open api）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_api_key")
public class WikiApiKey extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "item_id", nullable = false)
    public Long itemId;

    @Column(name = "api_key", nullable = false, unique = true, length = 64)
    public String apiKey;

    @Column(name = "api_secret", nullable = false, length = 128)
    public String apiSecret;

    @Column(length = 255)
    public String remark;

    @Column(name = "created_by")
    public Long createdBy;

    @Column(name = "created_by_name", length = 64)
    public String createdByName;

    @Column(nullable = false)
    public Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    public static List<WikiApiKey> listByItem(Long itemId) {
        return list("itemId = ?1 and deleted = false", Sort.by("id").descending(), itemId);
    }
}
