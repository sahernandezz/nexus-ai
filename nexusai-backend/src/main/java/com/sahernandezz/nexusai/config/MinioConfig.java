package com.sahernandezz.nexusai.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO object-storage configuration.
 * Bucket is auto-created on startup if it doesn't exist.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${minio.bucket:nexusai-documents}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        log.info("[MinIO] Connecting to {} bucket={}", endpoint, bucket);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket(client);
        return client;
    }

    @Bean
    public String minioBucketName() {
        return bucket;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void ensureBucket(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] Created bucket: {}", bucket);
            } else {
                log.info("[MinIO] Bucket already exists: {}", bucket);
            }
        } catch (Exception e) {
            log.error("[MinIO] Failed to ensure bucket '{}': {}", bucket, e.getMessage(), e);
            throw new RuntimeException("MinIO bucket initialization failed: " + e.getMessage(), e);
        }
    }
}

