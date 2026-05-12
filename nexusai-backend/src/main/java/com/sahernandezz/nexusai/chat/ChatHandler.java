package com.sahernandezz.nexusai.chat;

import com.sahernandezz.nexusai.chat.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandler {

    private final ChatService chatService;
    private final Validator validator;

    // ── POST /api/chat/stream → text/event-stream ─────────────────────────────

    public Mono<ServerResponse> stream(ServerRequest request) {
        return request.bodyToMono(ChatRequest.class)
                .flatMap(this::validate)
                .flatMap(req -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(BodyInserters.fromServerSentEvents(chatService.stream(req))))
                .onErrorResume(ResponseStatusException.class, ex ->
                        ServerResponse.status(ex.getStatusCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage())));
    }

    // ── GET /api/chat/models ──────────────────────────────────────────────────

    public Mono<ServerResponse> models(ServerRequest request) {
        return chatService.modelsInfo()
                .flatMap(info -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(info));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<ChatRequest> validate(ChatRequest req) {
        var errors = new BeanPropertyBindingResult(req, "chatRequest");
        validator.validate(req, errors);
        if (errors.hasErrors()) {
            String msg = errors.getAllErrors().get(0).getDefaultMessage();
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, msg));
        }
        return Mono.just(req);
    }
}

