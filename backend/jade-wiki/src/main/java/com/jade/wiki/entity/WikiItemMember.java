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
 * 项目成员
 * member_group: 0=所有者 1=可编辑 2=只读
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_item_member")
public class WikiItemMember extends PanacheEntityBase {

    public static final int GROUP_OWNER = 0;
    public static final int GROUP_EDITABLE = 1;
    public static final int GROUP_READONLY = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "item_id", nullable = false)
    public Long itemId;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "member_group", nullable = false)
    public Integer memberGroup = GROUP_EDITABLE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    public static List<WikiItemMember> listByItem(Long itemId) {
        return list("itemId", Sort.by("memberGroup").ascending(), itemId);
    }

    public static WikiItemMember findByItemAndUser(Long itemId, Long userId) {
        return find("itemId = ?1 and userId = ?2", itemId, userId).firstResult();
    }

    public static long countByItem(Long itemId) {
        return count("itemId", itemId);
    }
}
