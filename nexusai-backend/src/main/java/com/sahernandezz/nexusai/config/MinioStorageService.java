package com.sahernandezz.nexusai.config;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for storing and retrieving documents in MinIO object storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final String minioBucketName;

    /**
     * Uploads an InputStream to MinIO and returns the object key.
     */
    public String upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            log.info("[MinIO] Uploaded object: {}", objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("[MinIO] Upload failed for key={}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Downloads an object from MinIO and returns its InputStream.
     * Caller is responsible for closing the stream.
     */
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("[MinIO] Download failed for key={}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("MinIO download failed: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes an object from MinIO.
     */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioBucketName)
                            .object(objectKey)
                            .build()
            );
            log.info("[MinIO] Deleted object: {}", objectKey);
        } catch (Exception e) {
            log.warn("[MinIO] Delete failed for key={}: {}", objectKey, e.getMessage());
        }
    }

    /**
     * Lists object keys under the given prefix. Used as a recovery path when
     * in-memory metadata (e.g. {@code DocumentIngestionService.statusMap}) is
     * lost after a backend restart but the bytes are still in the bucket.
     */
    public List<String> listKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioBucketName)
                            .prefix(prefix)
                            .recursive(true)
                            .build());
            for (Result<Item> r : results) {
                keys.add(r.get().objectName());
            }
        } catch (Exception e) {
            log.warn("[MinIO] listKeys failed for prefix={}: {}", prefix, e.getMessage());
        }
        return keys;
    }

    /**
     * Generates a pre-signed URL for direct download (valid for 1 hour).
     */
    public String presignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioBucketName)
                            .object(objectKey)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.error("[MinIO] Pre-signed URL failed for key={}: {}", objectKey, e.getMessage());
            throw new RuntimeException("MinIO presigned URL failed: " + e.getMessage(), e);
        }
    }
}

