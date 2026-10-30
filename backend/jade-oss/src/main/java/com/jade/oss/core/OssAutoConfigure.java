package com.jade.oss.core;

import com.jade.oss.local.LocalOssTemplate;
import com.jade.oss.s3.S3OssTemplate;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * OssTemplate 装配器：按 {@code jade.oss.provider} 运行时选择实现
 * <p>
 * 消费方统一注入 {@link OssTemplate} 即可，配置示例：
 * <pre>
 * jade:
 *   oss:
 *     provider: s3                # local(默认) | s3
 *     endpoint: http://localhost:9000
 *     region:   us-east-1         # 阿里 oss-cn-hangzhou / 华为 cn-north-4 ...
 *     bucket:   jade
 *     access-key: xxx
 *     secret-key: xxx
 *     path-style: true            # MinIO 必须 true
 * </pre>
 */
@ApplicationScoped
public class OssAutoConfigure implements OssTemplate {

    @ConfigProperty(name = "jade.oss.provider", defaultValue = "local")
    String provider;

    @ConfigProperty(name = "jade.oss.local.root-dir", defaultValue = "./data/oss")
    String localRootDir;

    @ConfigProperty(name = "jade.oss.endpoint")
    java.util.Optional<String> endpoint;

    @ConfigProperty(name = "jade.oss.region")
    java.util.Optional<String> region;

    @ConfigProperty(name = "jade.oss.bucket", defaultValue = "jade")
    String bucket;

    @ConfigProperty(name = "jade.oss.access-key")
    java.util.Optional<String> accessKey;

    @ConfigProperty(name = "jade.oss.secret-key")
    java.util.Optional<String> secretKey;

    @ConfigProperty(name = "jade.oss.path-style", defaultValue = "true")
    boolean pathStyle;

    @ConfigProperty(name = "jade.oss.create-bucket", defaultValue = "true")
    boolean createBucket;

    /** 懒加载：provider=s3 但未用到存储时不初始化客户端，也不打断启动 */
    private volatile OssTemplate delegate;

    private OssTemplate delegate() {
        OssTemplate d = delegate;
        if (d == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = "s3".equalsIgnoreCase(provider) ? buildS3() : new LocalOssTemplate(localRootDir);
                }
                d = delegate;
            }
        }
        return d;
    }

    private OssTemplate buildS3() {
        if (endpoint.isEmpty() || endpoint.get().isBlank()) {
            throw new IllegalStateException("jade.oss.provider=s3 需要配置 jade.oss.endpoint");
        }
        if (accessKey.isEmpty() || accessKey.get().isBlank()
                || secretKey.isEmpty() || secretKey.get().isBlank()) {
            throw new IllegalStateException("jade.oss.provider=s3 需要配置 jade.oss.access-key / jade.oss.secret-key");
        }
        return new S3OssTemplate(endpoint.get(), region.orElse("us-east-1"), bucket,
                accessKey.get(), secretKey.get(), pathStyle, createBucket);
    }

    @Override
    public String upload(String key, java.io.InputStream input, long size, String contentType) {
        return delegate().upload(key, input, size, contentType);
    }

    @Override
    public void delete(String key) {
        delegate().delete(key);
    }

    @Override
    public java.io.InputStream open(String key) {
        return delegate().open(key);
    }

    @Override
    public boolean exists(String key) {
        return delegate().exists(key);
    }

    @Override
    public String getSignedUrl(String key, long expireSeconds) {
        return delegate().getSignedUrl(key, expireSeconds);
    }

    @Override
    public String getBucket() {
        return delegate().getBucket();
    }
}
