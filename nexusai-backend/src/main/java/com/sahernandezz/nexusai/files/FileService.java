package com.sahernandezz.nexusai.files;

import com.sahernandezz.nexusai.config.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

/**
 * Generic per-user file storage backed by MinIO.
 *
 * Used for chat-inline attachments (images, screenshots, PDFs that aren't
 * RAG-indexed) and for export artifacts. RAG-indexed documents have their
 * own pipeline ({@code DocumentIngestionService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioStorageService minio;
    private final FileAttachmentRepository repo;

    /** Stores raw bytes that are already in memory (used by export worker). */
    public Mono<FileAttachment> storeBytes(UUID userId, UUID conversationId, byte[] bytes,
                                           String filename, String contentType, String kind) {
        UUID id = UUID.randomUUID();
        String key = "attachments/" + id + "/" + filename;
        return Mono.fromCallable(() -> {
                    try (InputStream is = new java.io.ByteArrayInputStream(bytes)) {
                        minio.upload(key, is, bytes.length,
                                contentType != null ? contentType : "application/octet-stream");
                    }
                    return new FileAttachment(id, userId, conversationId, filename,
                            contentType, (long) bytes.length, key, kind, Instant.now(), true);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(repo::save);
    }

    /** Stores a multipart upload from the user. */
    public Mono<FileAttachment> store(UUID userId, UUID conversationId, FilePart part, String kind) {
        UUID id = UUID.randomUUID();
        String filename = part.filename();
        String contentType = part.headers().getContentType() != null
                ? part.headers().getContentType().toString()
                : "application/octet-stream";
        String key = "attachments/" + id + "/" + filename;

        return Mono.fromCallable(() -> Files.createTempFile("nexusai-att-", "-" + filename))
                .flatMap(tmp -> part.transferTo(tmp).thenReturn(tmp))
                .flatMap(tmp -> Mono.fromCallable(() -> {
                            long size = Files.size(tmp);
                            try (InputStream is = Files.newInputStream(tmp)) {
                                minio.upload(key, is, size, contentType);
                            } finally {
                                try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
                            }
                            return new FileAttachment(id, userId, conversationId, filename,
                                    contentType, size, key, kind, Instant.now(), true);
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(repo::save)
                .doOnNext(att -> log.info("[FileService] Stored {} ({} bytes) → {}",
                        att.filename(), att.sizeBytes(), att.minioKey()));
    }

    /** Streams the file bytes from MinIO. Caller must verify ownership first. */
    public Mono<byte[]> readBytes(FileAttachment att) {
        return Mono.fromCallable(() -> {
            try (InputStream is = minio.download(att.minioKey())) {
                return is.readAllBytes();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<FileAttachment> findOwned(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId);
    }
}
