package com.sahernandezz.nexusai.files;

import com.sahernandezz.nexusai.auth.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileHandler {

    private final FileService fileService;
    private final UserDirectory userDirectory;

    /** POST /api/files — multipart upload. Optional ?conversationId=. */
    public Mono<ServerResponse> upload(ServerRequest request) {
        UUID conversationId = request.queryParam("conversationId")
                .map(UUID::fromString).orElse(null);
        String kind = request.queryParam("kind").orElse("inline");

        return userDirectory.currentUserId().flatMap(userId -> request.multipartData().flatMap(parts -> {
            var part = parts.getFirst("file");
            if (!(part instanceof FilePart filePart)) {
                return ServerResponse.badRequest()
                        .bodyValue(Map.of("error", "Expected multipart field 'file'"));
            }
            return fileService.store(userId, conversationId, filePart, kind)
                    .flatMap(att -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of(
                                    "id", att.id().toString(),
                                    "filename", att.filename(),
                                    "contentType", att.contentType(),
                                    "size", att.sizeBytes(),
                                    "url", "/api/files/" + att.id())));
        }));
    }

    /**
     * GET /api/files/{id} — streams bytes from MinIO with original Content-Type.
     */
    public Mono<ServerResponse> download(ServerRequest request) {
        UUID id;
        try {
            id = UUID.fromString(request.pathVariable("id"));
        } catch (IllegalArgumentException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "invalid id"));
        }

        return userDirectory.currentUserId()
                .flatMap(userId -> fileService.findOwned(id, userId))
                .flatMap(att -> fileService.readBytes(att).flatMap(bytes -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename(att.filename()).build());
                    return ServerResponse.ok()
                            .contentType(MediaType.parseMediaType(
                                    att.contentType() != null ? att.contentType() : "application/octet-stream"))
                            .headers(h -> h.addAll(headers))
                            .bodyValue(bytes);
                }))
                .switchIfEmpty(ServerResponse.notFound().build())
                .onErrorResume(ex -> {
                    log.error("[FileHandler] download failed for id={}: {}", id, ex.getMessage(), ex);
                    return ServerResponse.status(500)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", "No se pudo descargar el archivo"));
                });
    }
}

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class FileRouter {
    @Bean
    RouterFunction<ServerResponse> fileRoutes(FileHandler handler) {
        return RouterFunctions.route()
                .POST("/api/files", handler::upload)
                .GET("/api/files/{id}", handler::download)
                .build();
    }
}
