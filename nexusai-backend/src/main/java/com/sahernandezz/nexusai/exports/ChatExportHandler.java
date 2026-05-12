package com.sahernandezz.nexusai.exports;

import com.sahernandezz.nexusai.auth.UserDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatExportHandler {

    private final ChatExportService service;
    private final UserDirectory userDirectory;

    /** POST /api/exports — body {conversationId?: UUID}; null = export all. */
    public Mono<ServerResponse> create(ServerRequest req) {
        return req.bodyToMono(Map.class).defaultIfEmpty(Map.of()).flatMap(body ->
                userDirectory.currentUserId().flatMap(userId -> {
                    UUID convId = parseUuid(body.get("conversationId"));
                    return service.request(userId, convId)
                            .flatMap(e -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(toDto(e)));
                }))
                .onErrorResume(IllegalStateException.class, ex -> ServerResponse.notFound().build());
    }

    /** GET /api/exports/{id} — poll status. Returns {status, attachmentId?, downloadUrl?}. */
    public Mono<ServerResponse> get(ServerRequest req) {
        UUID id;
        try { id = UUID.fromString(req.pathVariable("id")); }
        catch (IllegalArgumentException e) {
            return ServerResponse.badRequest().bodyValue(Map.of("error", "invalid id"));
        }
        return userDirectory.currentUserId().flatMap(userId ->
                service.findOwned(id, userId)
                        .flatMap(e -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(toDto(e)))
                        .switchIfEmpty(ServerResponse.notFound().build()));
    }

    private static UUID parseUuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (Exception ignored) { return null; }
    }

    private static Map<String, Object> toDto(ChatExportEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id().toString());
        m.put("status", e.status());
        if (e.attachmentId() != null) {
            m.put("attachmentId", e.attachmentId().toString());
            m.put("downloadUrl", "/api/files/" + e.attachmentId());
        }
        if (e.errorMsg() != null) m.put("errorMsg", e.errorMsg());
        m.put("createdAt", e.createdAt().toString());
        m.put("updatedAt", e.updatedAt().toString());
        return m;
    }
}

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class ChatExportRouter {
    @Bean
    RouterFunction<ServerResponse> exportRoutes(ChatExportHandler h) {
        return RouterFunctions.route()
                .POST("/api/exports",      h::create)
                .GET("/api/exports/{id}",  h::get)
                .build();
    }
}
