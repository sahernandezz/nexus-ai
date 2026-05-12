package com.sahernandezz.nexusai.chat;

import com.sahernandezz.nexusai.cache.SemanticCacheService;
import com.sahernandezz.nexusai.chat.dto.ChatRequest;
import com.sahernandezz.nexusai.memory.ChatMemoryAdvisorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Core chat service — multi-LLM, streaming SSE, memory-aware, cache-aware.
 *
 * <p>
 * Stream emits two SSE event types:
 * <ul>
 * <li>{@code event: meta} — JSON with {@code cached} (boolean) and
 * {@code layer} (exact|semantic|null). Always sent first.</li>
 * <li>{@code event: message} — the response token stream (default event).</li>
 * </ul>
 */
@Slf4j
@Service
public class ChatService {

    private final ChatClientRegistry registry;
    private final SemanticCacheService cacheService;

    @Autowired(required = false)
    private ChatMemoryAdvisorFactory memoryFactory;

    public ChatService(ChatClientRegistry registry, SemanticCacheService cacheService) {
        this.registry = registry;
        this.cacheService = cacheService;
    }

    // ── Streaming (cache-aware) ──────────────────────────────────────────────

    /**
     * Cache-checked SSE stream.
     * <ol>
     * <li>Look up the prompt in the semantic cache.</li>
     * <li>If hit → emit {@code meta(cached=true, layer)} + the cached content.</li>
     * <li>If miss → emit {@code meta(cached=false)} + LLM stream, and store the
     * full response after it completes.</li>
     * </ol>
     */
    public Flux<ServerSentEvent<String>> stream(ChatRequest request) {
        log.debug("[ChatService] stream | provider={} session={}",
                request.resolvedProvider(), request.sessionId());

        return cacheService.lookup(request.message())
                .flatMapMany(opt -> {
                    if (opt.isPresent()) {
                        var cached = opt.get();
                        String content = cached.content();
                        log.info("[ChatService] cache hit | layer={} chars={}",
                                cached.layer(), content == null ? 0 : content.length());
                        // Defense against accidentally stored-empty entries.
                        if (content != null && !content.isBlank()) {
                            return Flux.concat(
                                    Flux.just(metaEvent(true, cached.layer())),
                                    chunked(content));
                        }
                        log.warn("[ChatService] cache hit had empty content — treating as miss");
                    }
                    StringBuilder buf = new StringBuilder();
                    Flux<ServerSentEvent<String>> tokens = buildSpec(request).stream().content()
                            .doOnNext(buf::append)
                            .map(this::messageEvent);
                    return Flux.concat(
                            Flux.just(metaEvent(false, null)),
                            tokens).doOnComplete(() -> {
                                String full = buf.toString();
                                if (!full.isBlank()) {
                                    cacheService.store(request.message(), full).subscribe();
                                }
                            });
                })
                .onErrorResume(ex -> {
                    log.error("[ChatService] stream error: {}", ex.getMessage());
                    return Flux.error(ex);
                });
    }

    // ── Available models ──────────────────────────────────────────────────────

    public Mono<Map<String, Object>> modelsInfo() {
        return Mono.just(Map.of(
                "providers", registry.availableProviders(),
                "chatModels", java.util.List.of("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "gpt-4o-mini"),
                "imageModels", java.util.List.of("gpt-image-1", "dall-e-3")));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private ChatClient.ChatClientRequestSpec buildSpec(ChatRequest request) {
        ChatClient client = registry.get(request.resolvedProvider());
        ChatClient.ChatClientRequestSpec spec = client.prompt().user(request.message());

        if (request.sessionId() != null && !request.sessionId().isBlank()
                && memoryFactory != null) {
            spec = spec.advisors(memoryFactory.forSession(request.sessionId()));
        }

        boolean hasModel = request.model() != null && !request.model().isBlank();
        boolean hasTemp = request.temperature() != null;

        if (hasModel || hasTemp) {
            OpenAiChatOptions.Builder opts = OpenAiChatOptions.builder();
            if (hasModel)
                opts.model(request.model());
            if (hasTemp)
                opts.temperature(request.temperature());
            spec = spec.options(opts.build());
        }

        return spec;
    }

    private ServerSentEvent<String> metaEvent(boolean cached, String layer) {
        String json = layer != null
                ? "{\"cached\":" + cached + ",\"layer\":\"" + layer + "\"}"
                : "{\"cached\":" + cached + "}";
        return ServerSentEvent.<String>builder()
                .event("meta")
                .data(json)
                .build();
    }

    private ServerSentEvent<String> messageEvent(String token) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(token)
                .build();
    }

    /** See {@code RagService#chunked} — same rationale (robustness + UX). */
    private static final int CHUNK_SIZE = 80;

    private Flux<ServerSentEvent<String>> chunked(String content) {
        if (content.length() <= CHUNK_SIZE) return Flux.just(messageEvent(content));
        return Flux.create(sink -> {
            int i = 0;
            while (i < content.length()) {
                int end = Math.min(i + CHUNK_SIZE, content.length());
                sink.next(messageEvent(content.substring(i, end)));
                i = end;
            }
            sink.complete();
        });
    }
}
