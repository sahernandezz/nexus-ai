package com.sahernandezz.nexusai.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticCacheService Unit Tests")
class SemanticCacheServiceTest {

    @Mock ReactiveStringRedisTemplate redisTemplate;
    @Mock ReactiveValueOperations<String, String> valueOps;
    @Mock EmbeddingModel embeddingModel;
    @Mock SemanticCacheRepository cacheRepository;

    SemanticCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new SemanticCacheService(redisTemplate, embeddingModel, new SimpleMeterRegistry(), cacheRepository);
        // Set private fields via reflection
        setField(service, "ttlSeconds", 3600L);
        setField(service, "similarityThreshold", 0.92);
    }

    @Test
    @DisplayName("lookup — returns exact hit from Redis when key is found")
    void lookup_exactHit_returnsFromRedis() {
        when(valueOps.get(anyString())).thenReturn(Mono.just("cached answer"));

        StepVerifier.create(service.lookup("What is Java?"))
                .assertNext(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().content()).isEqualTo("cached answer");
                    assertThat(opt.get().fromCache()).isTrue();
                    assertThat(opt.get().layer()).isEqualTo("exact");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("lookup — falls through to semantic lookup on Redis miss")
    void lookup_redisMiss_fallsToSemantic() {
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(embeddingModel.embed(anyString()))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        SemanticCacheEntry entry = new SemanticCacheEntry("What is Java?", "Java is a language.", new float[]{0.1f, 0.2f, 0.3f});
        when(cacheRepository.findBySimilarity(any(float[].class), anyDouble()))
                .thenReturn(Optional.of(entry));

        StepVerifier.create(service.lookup("What is Java?"))
                .assertNext(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().layer()).isEqualTo("semantic");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("lookup — returns empty Optional on total cache miss")
    void lookup_totalMiss_returnsEmpty() {
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(embeddingModel.embed(anyString()))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(cacheRepository.findBySimilarity(any(float[].class), anyDouble()))
                .thenReturn(Optional.empty());

        StepVerifier.create(service.lookup("unique novel question"))
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("lookup — returns empty on Redis error (graceful degradation)")
    void lookup_redisError_degradesGracefully() {
        when(valueOps.get(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

        StepVerifier.create(service.lookup("any prompt"))
                .assertNext(opt -> assertThat(opt).isEmpty())
                .verifyComplete();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void setField(Object target, String field, Object value) {
        try {
            var f = SemanticCacheService.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
