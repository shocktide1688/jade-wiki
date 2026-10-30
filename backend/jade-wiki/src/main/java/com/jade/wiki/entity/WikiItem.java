package com.jade.wiki.entity;

import com.jade.security.annotation.Encrypted;
import com.jade.security.annotation.TenantId;
import com.jade.security.crypto.EncryptedStringConverter;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Wiki 项目/空间（类 ShowDoc 的 item）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_item")
public class WikiItem extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @TenantId
    @Column(name = "tenant_id")
    public Long tenantId;

    @Column(name = "item_name", nullable = false, length = 128)
    public String itemName;

    @Column(name = "item_description", length = 512)
    public String itemDescription;

    /** public / private */
    @Column(nullable = false, length = 20)
    public String visibility = "private";

    /** 访问密码（AES-256-GCM 加密存储） */
    @Encrypted
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 512)
    public String password;

    /** regular=常规项目 single=单页项目 */
    @Column(name = "item_type", nullable = false, length = 20)
    public String itemType = "regular";

    @Column(name = "creator_id", nullable = false)
    public Long creatorId;

    @Column(name = "creator_name", length = 64)
    public String creatorName;

    @Column(name = "is_archive", nullable = false)
    public Boolean isArchive = false;

    @Column(nullable = false)
    public Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    public static WikiItem findActiveById(Long id) {
        return find("id = ?1 and deleted = false", id).firstResult();
    }

    /** 我的项目：我创建的 + 我是成员的（DB 级分页查询） */
    public static PanacheQuery<WikiItem> findMine(Long userId, String keyword) {
        String base = "deleted = false and (creatorId = ?1 or id in "
                + "(select m.itemId from WikiItemMember m where m.userId = ?1))";
        if (keyword == null || keyword.isBlank()) {
            return find(base, Sort.by("updatedAt").descending(), userId);
        }
        return find(base + " and lower(itemName) like ?2",
                Sort.by("updatedAt").descending(), userId, "%" + keyword.toLowerCase() + "%");
    }
}
