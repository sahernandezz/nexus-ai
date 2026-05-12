package com.sahernandezz.nexusai.rag;

import com.sahernandezz.nexusai.cache.SemanticCacheService;
import com.sahernandezz.nexusai.chat.ChatClientRegistry;
import com.sahernandezz.nexusai.chat.dto.ChatRequest;
import com.sahernandezz.nexusai.rag.retrieval.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagService Unit Tests")
class RagServiceTest {

    @Mock ChatClientRegistry registry;
    @Mock VectorStore vectorStore;
    @Mock SemanticCacheService cacheService;
    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.StreamResponseSpec streamSpec;

    RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(registry, vectorStore, cacheService);
    }

    @Test
    @DisplayName("streamWithRag — emits meta+token events on cache miss")
    void streamWithRag_delegatesToChatClient() {
        when(cacheService.lookup(anyString())).thenReturn(Mono.just(Optional.empty()));
        when(cacheService.store(anyString(), anyString())).thenReturn(Mono.empty());
        when(registry.get(anyString())).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Hello", " World"));

        ChatRequest req = new ChatRequest("What is AI?", null, "openai", null, null, true);

        StepVerifier.create(ragService.streamWithRag(req, null))
                .assertNext(e -> assertThat(e.event()).isEqualTo("meta"))
                .assertNext(e -> assertEvent(e, "message", "Hello"))
                .assertNext(e -> assertEvent(e, "message", " World"))
                .verifyComplete();

        verify(registry).get("openai");
    }

    @Test
    @DisplayName("streamWithRag — applies documentId filter when provided")
    void streamWithRag_withDocumentId_appliesFilter() {
        when(cacheService.lookup(anyString())).thenReturn(Mono.just(Optional.empty()));
        when(cacheService.store(anyString(), anyString())).thenReturn(Mono.empty());
        when(registry.get(anyString())).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Filtered"));

        ChatRequest req = new ChatRequest("Summarize this doc", null, "openai", null, null, true);

        StepVerifier.create(ragService.streamWithRag(req, "doc-123"))
                .assertNext(e -> assertThat(e.event()).isEqualTo("meta"))
                .assertNext(e -> assertEvent(e, "message", "Filtered"))
                .verifyComplete();
    }

    @Test
    @DisplayName("streamWithRag — propagates errors from ChatClient")
    void streamWithRag_propagatesError() {
        when(cacheService.lookup(anyString())).thenReturn(Mono.just(Optional.empty()));
        when(registry.get(anyString())).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(org.springframework.ai.chat.client.advisor.api.Advisor[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.error(new RuntimeException("LLM error")));

        ChatRequest req = new ChatRequest("question", null, "openai", null, null, true);

        StepVerifier.create(ragService.streamWithRag(req, null))
                .assertNext(e -> assertThat(e.event()).isEqualTo("meta"))
                .expectErrorMatches(ex -> ex.getMessage().contains("LLM error"))
                .verify();
    }

    private static void assertEvent(ServerSentEvent<String> e, String name, String data) {
        assertThat(e.event()).isEqualTo(name);
        assertThat(e.data()).isEqualTo(data);
    }
}
