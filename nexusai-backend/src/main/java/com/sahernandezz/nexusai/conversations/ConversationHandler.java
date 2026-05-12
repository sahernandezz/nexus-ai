package com.sahernandezz.nexusai.conversations;

import com.sahernandezz.nexusai.auth.UserDirectory;
import com.sahernandezz.nexusai.conversations.dto.ConversationDto;
import com.sahernandezz.nexusai.conversations.dto.MessageDto;
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

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConversationHandler {

    private final ConversationService service;
    private final UserDirectory userDirectory;

    /** GET /api/conversations — list (without messages). */
    public Mono<ServerResponse> list(ServerRequest req) {
        return userDirectory.currentUserId().flatMap(userId ->
                ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                        .body(service.listForUser(userId), ConversationDto.class));
    }

    /** GET /api/conversations/{id} — full conversation with messages. */
    public Mono<ServerResponse> get(ServerRequest req) {
        UUID id;
        try { id = UUID.fromString(req.pathVariable("id")); }
        catch (IllegalArgumentException e) { return badId(); }

        return userDirectory.currentUserId().flatMap(userId ->
                service.getWithMessages(id, userId)
                        .flatMap(c -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(c))
                        .switchIfEmpty(ServerResponse.notFound().build()));
    }

    /** POST /api/conversations — create. Body: {id?, title?, model?}.
     *  When {@code id} is provided the backend uses it (idempotent insert);
     *  this lets the frontend keep its locally-generated UUID and avoid an
     *  id-swap dance + race condition with the message PUTs. */
    public Mono<ServerResponse> create(ServerRequest req) {
        return req.bodyToMono(Map.class).defaultIfEmpty(Map.of()).flatMap(body ->
                userDirectory.currentUserId().flatMap(userId -> {
                    UUID requestedId = parseUuid(body.get("id"));
                    return service.create(requestedId, userId,
                                    (String) body.get("title"), (String) body.get("model"))
                            .flatMap(c -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(c));
                }));
    }

    private static UUID parseUuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (Exception e) { return null; }
    }

    /** PATCH /api/conversations/{id} — rename. Body: {title}. */
    public Mono<ServerResponse> rename(ServerRequest req) {
        UUID id;
        try { id = UUID.fromString(req.pathVariable("id")); }
        catch (IllegalArgumentException e) { return badId(); }

        return req.bodyToMono(Map.class).flatMap(body ->
                userDirectory.currentUserId().flatMap(userId ->
                        service.rename(id, userId, (String) body.get("title"))
                                .flatMap(c -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(c))
                                .switchIfEmpty(ServerResponse.notFound().build())));
    }

    /** DELETE /api/conversations/{id}. */
    public Mono<ServerResponse> delete(ServerRequest req) {
        UUID id;
        try { id = UUID.fromString(req.pathVariable("id")); }
        catch (IllegalArgumentException e) { return badId(); }

        return userDirectory.currentUserId().flatMap(userId ->
                service.delete(id, userId).then(ServerResponse.noContent().build()));
    }

    /** PUT /api/conversations/{id}/messages — upsert message (body MessageDto). */
    public Mono<ServerResponse> upsertMessage(ServerRequest req) {
        UUID id;
        try { id = UUID.fromString(req.pathVariable("id")); }
        catch (IllegalArgumentException e) { return badId(); }

        return req.bodyToMono(MessageDto.class).flatMap(dto ->
                userDirectory.currentUserId().flatMap(userId ->
                        service.upsertMessage(id, userId, dto)
                                .flatMap(saved -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(saved))))
                .onErrorResume(IllegalStateException.class, ex -> ServerResponse.notFound().build());
    }

    /** DELETE /api/conversations/{id}/messages/{clientId}. */
    public Mono<ServerResponse> deleteMessage(ServerRequest req) {
        UUID id, clientId;
        try {
            id = UUID.fromString(req.pathVariable("id"));
            clientId = UUID.fromString(req.pathVariable("clientId"));
        } catch (IllegalArgumentException e) { return badId(); }

        return userDirectory.currentUserId().flatMap(userId ->
                service.deleteMessage(id, userId, clientId)
                        .then(ServerResponse.noContent().build()));
    }

    private static Mono<ServerResponse> badId() {
        return ServerResponse.badRequest().bodyValue(Map.of("error", "invalid id"));
    }
}

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class ConversationRouter {
    @Bean
    RouterFunction<ServerResponse> conversationRoutes(ConversationHandler h) {
        return RouterFunctions.route()
                .GET("/api/conversations",                                    h::list)
                .POST("/api/conversations",                                   h::create)
                .GET("/api/conversations/{id}",                               h::get)
                .PATCH("/api/conversations/{id}",                             h::rename)
                .DELETE("/api/conversations/{id}",                            h::delete)
                .PUT("/api/conversations/{id}/messages",                      h::upsertMessage)
                .DELETE("/api/conversations/{id}/messages/{clientId}",        h::deleteMessage)
                .build();
    }
}
