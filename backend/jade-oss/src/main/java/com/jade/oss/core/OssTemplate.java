package com.jade.oss.core;

import java.io.InputStream;

/**
 * 对象存储统一接口（本地磁盘 / S3 兼容：MinIO / 阿里 OSS / 华为 OBS / 腾讯 COS）
 * <p>
 * 实现由 {@link OssAutoConfigure} 按 jade.oss.provider 装配（local=默认本地磁盘, s3=S3 兼容）。
 * key 形如 {@code 2026/10/uuid.png}，由调用方保证唯一性。
 */
public interface OssTemplate {

    /** 上传文件，返回访问 URL（本地实现返回 file:// URI，S3 返回对象访问地址） */
    String upload(String key, InputStream input, long size, String contentType);

    /** 删除文件 */
    void delete(String key);

    /** 打开读取流（调用方负责关闭） */
    InputStream open(String key);

    /** 对象是否存在 */
    boolean exists(String key);

    /** 获取签名 URL（私有 bucket 临时授权下载；本地存储无此概念，返回 null） */
    String getSignedUrl(String key, long expireSeconds);

    /** 桶名（本地实现为根目录路径） */
    String getBucket();
}
