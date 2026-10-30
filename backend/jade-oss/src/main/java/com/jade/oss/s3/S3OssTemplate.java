package com.jade.oss.s3;

import com.jade.oss.core.OssTemplate;
import org.jboss.logging.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * S3 兼容对象存储（MinIO / 阿里 OSS / 华为 OBS / 腾讯 COS）
 * <p>
 * 一套 S3 协议通吃：各家只需换 endpoint/region，MinIO 需 path-style=true。
 */
public class S3OssTemplate implements OssTemplate {

    private static final Logger LOG = Logger.getLogger(S3OssTemplate.class);

    private final String bucket;
    private final boolean createBucketIfMissing;
    private final URI endpoint;
    private final Region region;
    private final S3Configuration serviceConfig;
    private final StaticCredentialsProvider credentialsProvider;
    private final S3Client client;
    private volatile S3Presigner presigner;

    public S3OssTemplate(String endpoint, String region, String bucket,
                         String accessKey, String secretKey,
                         boolean pathStyle, boolean createBucketIfMissing) {
        this.endpoint = URI.create(endpoint);
        this.region = Region.of(region);
        this.bucket = bucket;
        this.createBucketIfMissing = createBucketIfMissing;
        this.serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                // 关闭 aws-chunked 流式签名: 部分 S3 兼容实现(老 MinIO/网关)不解码该编码
                .chunkedEncodingEnabled(false)
                .build();
        this.credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
        this.client = S3Client.builder()
                .endpointOverride(this.endpoint)
                .region(this.region)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(serviceConfig)
                .httpClient(UrlConnectionHttpClient.create())
                // 关闭默认的尾随校验和: 部分 S3 兼容实现不支持 x-amz-checksum-crc32 trailer
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
        ensureBucket();
        LOG.infof("OssTemplate=s3 endpoint=%s bucket=%s path-style=%s", endpoint, bucket, pathStyle);
    }

    /** bucket 不存在时自动创建（自建 MinIO 场景；云厂商 IAM 不给建桶权限则只告警不阻断） */
    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return;
        } catch (NoSuchBucketException e) {
            // 落到下面的创建逻辑
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                LOG.warnf(e, "bucket 探测失败(跳过自动创建): %s", bucket);
                return;
            }
        }
        if (!createBucketIfMissing) {
            return;
        }
        try {
            var req = CreateBucketRequest.builder().bucket(bucket);
            if (!"us-east-1".equals(region.id())) {
                req.createBucketConfiguration(CreateBucketConfiguration.builder()
                        .locationConstraint(region.id()).build());
            }
            client.createBucket(req.build());
            LOG.infof("已自动创建 bucket: %s", bucket);
        } catch (BucketAlreadyOwnedByYouException ignored) {
        } catch (Exception e) {
            LOG.warnf(e, "bucket 自动创建失败(请手工创建): %s", bucket);
        }
    }

    @Override
    public String upload(String key, InputStream input, long size, String contentType) {
        var builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);
        if (input.markSupported()) {
            // 可重放流直接送 (SDK 需要整流读一遍算签名)
            client.putObject(builder.contentLength(size).build(),
                    RequestBody.fromInputStream(input, size));
        } else {
            // 非重放流先落临时文件: http 端点下 SDK 需整流计算签名, 重试也要求可重读
            try {
                Path tmp = Files.createTempFile("jade-oss-", ".tmp");
                try {
                    long actual = Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING);
                    client.putObject(builder.contentLength(actual).build(), RequestBody.fromFile(tmp));
                } finally {
                    Files.deleteIfExists(tmp);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("对象暂存失败: " + key, e);
            }
        }
        return accessUrl(key);
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Override
    public String getSignedUrl(String key, long expireSeconds) {
        S3Presigner p = presigner;
        if (p == null) {
            synchronized (this) {
                if (presigner == null) {
                    presigner = S3Presigner.builder()
                            .endpointOverride(endpoint)
                            .region(region)
                            .credentialsProvider(credentialsProvider)
                            .serviceConfiguration(serviceConfig)
                            .build();
                }
                p = presigner;
            }
        }
        PresignedGetObjectRequest pre = p.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expireSeconds))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build());
        return pre.url().toString();
    }

    @Override
    public String getBucket() {
        return bucket;
    }

    /** 对象访问地址: path-style 为 endpoint/bucket/key, virtual-host 为 bucket.endpoint/key */
    private String accessUrl(String key) {
        String base = endpoint.toString().replaceAll("/$", "");
        if (Boolean.TRUE.equals(serviceConfig.pathStyleAccessEnabled())) {
            return base + "/" + bucket + "/" + key;
        }
        return endpoint.getScheme() + "://" + bucket + "." + endpoint.getRawAuthority() + "/" + key;
    }
}
