package com.sahernandezz.nexusai.rag.ingestion;

import com.sahernandezz.nexusai.config.MinioStorageService;
import com.sahernandezz.nexusai.config.RabbitMQConfig;
import com.sahernandezz.nexusai.rag.dto.DocumentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handles document upload to MinIO and publishes an {@link IngestEvent} to RabbitMQ.
 * The actual document processing happens in {@link DocumentIngestionConsumer}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final RabbitTemplate rabbitTemplate;
    private final MinioStorageService minioStorageService;

    /** In-memory status store (replaced by DB in full impl). */
    private final ConcurrentMap<String, DocumentDto> statusMap = new ConcurrentHashMap<>();

    // ── Upload & Publish ──────────────────────────────────────────────────────

    public Mono<DocumentDto> ingest(FilePart filePart) {
        return Mono.fromCallable(() -> {
            String docId = UUID.randomUUID().toString();
            String filename = filePart.filename();
            String contentType = resolveContentType(filename);

            DocumentDto dto = new DocumentDto(
                    docId, filename, contentType,
                    null, DocumentStatus.PENDING, null,
                    Instant.now(), Instant.now());

            statusMap.put(docId, dto);
            log.info("[RAG] Document received: {} ({})", filename, docId);
            return dto;
        })
        .flatMap(dto -> saveToTempAndUpload(filePart, dto)
                .flatMap(minioKey -> publishIngestEvent(dto, minioKey))
                .thenReturn(dto));
    }

    public Mono<DocumentDto> getStatus(String documentId) {
        DocumentDto dto = statusMap.get(documentId);
        return dto != null ? Mono.just(dto) : Mono.empty();
    }

    public Flux<DocumentDto> listAll() {
        return Flux.fromIterable(statusMap.values())
                .sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
    }

    public Mono<Void> delete(String documentId) {
        DocumentDto dto = statusMap.remove(documentId);
        if (dto != null) {
            String minioKey = "documents/" + documentId + "/" + dto.filename();
            return Mono.fromRunnable(() -> minioStorageService.delete(minioKey))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then();
        }
        return Mono.empty();
    }

    void updateStatus(String documentId, DocumentStatus status, String errorMsg) {
        statusMap.computeIfPresent(documentId, (id, dto) ->
                new DocumentDto(dto.id(), dto.filename(), dto.contentType(),
                        dto.sizeBytes(), status, errorMsg,
                        dto.createdAt(), Instant.now()));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Saves the uploaded file to a temp path first, then uploads to MinIO.
     * Returns the MinIO object key.
     */
    private Mono<String> saveToTempAndUpload(FilePart filePart, DocumentDto dto) {
        return Mono.fromCallable(() -> Files.createTempFile("nexusai-", "-" + filePart.filename()))
                .flatMap(tmp -> filePart.transferTo(tmp).thenReturn(tmp))
                .flatMap(tmp -> Mono.fromCallable(() -> {
                    String minioKey = "documents/" + dto.id() + "/" + filePart.filename();
                    try (InputStream is = Files.newInputStream(tmp)) {
                        long size = Files.size(tmp);
                        minioStorageService.upload(minioKey, is, size, dto.contentType());
                        log.info("[RAG] Uploaded to MinIO: key={} size={}", minioKey, size);
                        return minioKey;
                    } finally {
                        try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
                    }
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> publishIngestEvent(DocumentDto dto, String minioKey) {
        return Mono.fromRunnable(() -> {
            IngestEvent event = new IngestEvent(
                    dto.id(), dto.filename(), minioKey,
                    dto.contentType(), dto.sizeBytes());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DOCUMENT_EXCHANGE,
                    RabbitMQConfig.DOCUMENT_INGEST_KEY,
                    event);

            log.info("[RAG] Ingest event published: docId={} key={}", dto.id(), minioKey);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private String resolveContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".txt"))  return "text/plain";
        if (lower.endsWith(".md"))   return "text/markdown";
        if (lower.endsWith(".html")) return "text/html";
        return "application/octet-stream";
    }
}
