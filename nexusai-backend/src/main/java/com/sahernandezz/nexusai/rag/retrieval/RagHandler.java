package com.sahernandezz.nexusai.rag.retrieval;

import com.sahernandezz.nexusai.chat.dto.ChatRequest;
import com.sahernandezz.nexusai.config.MinioStorageService;
import com.sahernandezz.nexusai.rag.ingestion.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RagHandler {

    private final RagService ragService;
    private final DocumentIngestionService ingestionService;
    private final MinioStorageService minioStorageService;

    /** POST /api/rag/upload  — multipart file upload */
    public Mono<ServerResponse> upload(ServerRequest request) {
        return request.multipartData()
                .flatMap(parts -> {
                    var part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return ServerResponse.badRequest()
                                .bodyValue(Map.of("error", "Expected a multipart field named 'file'"));
                    }
                    return ingestionService.ingest(filePart)
                            .flatMap(dto -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(dto));
                });
    }

    /** GET /api/rag/documents — list all documents */
    public Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ingestionService.listAll(), com.sahernandezz.nexusai.rag.dto.DocumentDto.class);
    }

    /** GET /api/rag/documents/{id} — get document status */
    public Mono<ServerResponse> status(ServerRequest request) {
        String id = request.pathVariable("id");
        return ingestionService.getStatus(id)
                .flatMap(dto -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(dto))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * GET /api/rag/documents/{id}/file — stream the original file from MinIO.
     *
     * Falls back to a MinIO {@code listKeys} probe if the in-memory metadata
     * map ({@code DocumentIngestionService.statusMap}) was lost after a
     * backend restart, so reopened browsers can still load PDFs that were
     * uploaded in a previous backend lifecycle.
     */
    public Mono<ServerResponse> getFile(ServerRequest request) {
        String id = request.pathVariable("id");
        return resolveDocumentFile(id)
                .flatMap(meta -> Mono.fromCallable(() -> {
                            try (InputStream is = minioStorageService.download(meta.key())) {
                                return is.readAllBytes();
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(bytes -> ServerResponse.ok()
                                .contentType(MediaType.parseMediaType(
                                        meta.contentType() != null ? meta.contentType() : "application/octet-stream"))
                                .headers(h -> h.setContentDisposition(ContentDisposition.inline()
                                        .filename(meta.filename())
                                        .build()))
                                .bodyValue(bytes)))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    /**
     * Resolves a document's MinIO metadata. Prefers the in-memory status map
     * (cheap), falls back to a {@code documents/{id}/} prefix listing in
     * MinIO so we survive backend restarts that wipe the map.
     */
    private Mono<DocFileMeta> resolveDocumentFile(String id) {
        return ingestionService.getStatus(id)
                .map(dto -> new DocFileMeta(
                        "documents/" + id + "/" + dto.filename(),
                        dto.filename(),
                        dto.contentType()))
                .switchIfEmpty(Mono.fromCallable(() -> {
                            var keys = minioStorageService.listKeys("documents/" + id + "/");
                            if (keys.isEmpty()) return null;
                            String key = keys.get(0);
                            String filename = key.substring(key.lastIndexOf('/') + 1);
                            return new DocFileMeta(key, filename, contentTypeFor(filename));
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(meta -> meta == null ? Mono.empty() : Mono.just(meta)));
    }

    private static String contentTypeFor(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".txt"))  return "text/plain";
        if (lower.endsWith(".md"))   return "text/markdown";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private record DocFileMeta(String key, String filename, String contentType) {}

    /** DELETE /api/rag/documents/{id} */
    public Mono<ServerResponse> delete(ServerRequest request) {
        String id = request.pathVariable("id");
        return ingestionService.delete(id)
                .then(ServerResponse.noContent().build());
    }

    /** POST /api/rag/stream — RAG-augmented streaming chat */
    public Mono<ServerResponse> stream(ServerRequest request) {
        return request.bodyToMono(Map.class)
                .flatMap(body -> {
                    String message    = (String) body.get("message");
                    String documentId = (String) body.getOrDefault("documentId", "");
                    String provider   = (String) body.getOrDefault("provider", "openai");
                    String sessionId  = (String) body.getOrDefault("sessionId", null);

                    if (message == null || message.isBlank()) {
                        return ServerResponse.badRequest()
                                .bodyValue(Map.of("error", "message is required"));
                    }

                    ChatRequest chatReq = new ChatRequest(message, sessionId, provider, null, null, true);

                    return ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(BodyInserters.fromServerSentEvents(
                                    ragService.streamWithRag(chatReq, documentId)));
                });
    }
}

