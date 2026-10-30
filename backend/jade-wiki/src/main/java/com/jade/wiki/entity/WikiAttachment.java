package com.jade.wiki.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
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

/**
 * 附件/图片（文件存本地磁盘, 表记元数据）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "wiki_attachment")
public class WikiAttachment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "item_id")
    public Long itemId;

    /** 存储文件名（uuid.suffix） */
    @Column(name = "file_name", nullable = false, length = 255)
    public String fileName;

    @Column(name = "original_name", length = 255)
    public String originalName;

    @Column(name = "file_suffix", length = 32)
    public String fileSuffix;

    @Column(name = "file_size")
    public Long fileSize;

    @Column(name = "content_type", length = 128)
    public String contentType;

    @Column(name = "storage_path", nullable = false, length = 512)
    public String storagePath;

    @Column(name = "uploader_id")
    public Long uploaderId;

    @Column(name = "uploader_name", length = 64)
    public String uploaderName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;
}
