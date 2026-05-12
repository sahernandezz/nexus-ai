package com.sahernandezz.nexusai.chat;

import com.sahernandezz.nexusai.cache.SemanticCacheService;
import com.sahernandezz.nexusai.chat.dto.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService — Unit Tests")
class ChatServiceTest {

    @Mock private ChatClientRegistry registry;
    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.StreamResponseSpec streamSpec;
    @Mock private ChatClient.CallResponseSpec callSpec;
    @Mock private SemanticCacheService cacheService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(registry, cacheService);
    }

    // ── stream() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stream(): emits meta+token events on cache miss")
    void shouldStreamTokens() {
        when(cacheService.lookup(anyString())).thenReturn(Mono.just(Optional.empty()));
        when(cacheService.store(anyString(), anyString())).thenReturn(Mono.empty());
        when(registry.get("openai")).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Hello", " world", "!"));

        ChatRequest req = new ChatRequest("Say hello", null, "openai", null, null, true);

        StepVerifier.create(chatService.stream(req))
                .assertNext(e -> {
                    assertThat(e.event()).isEqualTo("meta");
                    assertThat(e.data()).contains("\"cached\":false");
                })
                .assertNext(e -> assertEvent(e, "message", "Hello"))
                .assertNext(e -> assertEvent(e, "message", " world"))
                .assertNext(e -> assertEvent(e, "message", "!"))
                .verifyComplete();
    }

    @Test
    @DisplayName("stream(): empty response still emits meta event")
    void shouldHandleEmptyStream() {
        when(cacheService.lookup(anyString())).thenReturn(Mono.just(Optional.empty()));
        when(registry.get("openai")).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.empty());

        ChatRequest req = new ChatRequest("Hello", null, "openai", null, null, true);

        StepVerifier.create(chatService.stream(req))
                .assertNext(e -> assertThat(e.event()).isEqualTo("meta"))
                .verifyComplete();
    }

    @Test
    @DisplayName("stream(): propagates error from ChatClient")
    void shouldPropagateStreamError() {
        when(cacheService.lookup(anyString())).thenReturn(Mono.just(Optional.empty()));
        when(registry.get("openai")).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.error(new RuntimeException("API error")));

        ChatRequest req = new ChatRequest("Hello", null, "openai", null, null, true);

        StepVerifier.create(chatService.stream(req))
                .assertNext(e -> assertThat(e.event()).isEqualTo("meta"))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ── modelsInfo() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("modelsInfo(): returns available providers list")
    void shouldReturnModelsInfo() {
        when(registry.availableProviders()).thenReturn(List.of("openai", "ollama"));

        StepVerifier.create(chatService.modelsInfo())
                .assertNext(info -> {
                    assertThat(info).containsKey("providers");
                    @SuppressWarnings("unchecked")
                    var providers = (List<String>) info.get("providers");
                    assertThat(providers).containsExactly("openai", "ollama");
                })
                .verifyComplete();
    }

    private static void assertEvent(ServerSentEvent<String> e, String name, String data) {
        assertThat(e.event()).isEqualTo(name);
        assertThat(e.data()).isEqualTo(data);
    }
}
