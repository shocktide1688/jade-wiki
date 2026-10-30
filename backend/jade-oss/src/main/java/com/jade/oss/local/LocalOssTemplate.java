package com.jade.oss.local;

import com.jade.oss.core.OssTemplate;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘对象存储（默认实现，开发/单机部署用）
 * <p>
 * 对象按 key 相对 rootDir 存取，key 形如 {@code 2026/10/uuid.png}，
 * 实际落盘 {@code <rootDir>/2026/10/uuid.png}。
 */
public class LocalOssTemplate implements OssTemplate {

    private static final Logger LOG = Logger.getLogger(LocalOssTemplate.class);

    private final Path rootDir;

    public LocalOssTemplate(String rootDir) {
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建 OSS 本地根目录: " + this.rootDir, e);
        }
        LOG.infof("OssTemplate=local root=%s", this.rootDir);
    }

    @Override
    public String upload(String key, InputStream input, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toUri().toString();
        } catch (IOException e) {
            throw new UncheckedIOException("文件写入失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("文件删除失败: " + key, e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("文件不存在: " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public String getSignedUrl(String key, long expireSeconds) {
        return null; // 本地存储无签名 URL，下载走应用自身鉴权接口
    }

    @Override
    public String getBucket() {
        return rootDir.toString();
    }

    /** key 越界防护: 禁止 ../ 逃出根目录 */
    private Path resolve(String key) {
        Path p = rootDir.resolve(key).normalize();
        if (!p.startsWith(rootDir)) {
            throw new IllegalArgumentException("非法对象 key: " + key);
        }
        return p;
    }
}
