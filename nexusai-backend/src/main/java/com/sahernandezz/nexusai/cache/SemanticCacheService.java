package com.sahernandezz.nexusai.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * M5 — Two-layer semantic cache:
 * <ol>
 *   <li>Redis exact-match cache (SHA-256 key of the prompt)</li>
 *   <li>pgvector cosine-similarity cache (semantic match above threshold)</li>
 * </ol>
 * Metrics:
 *   nexusai.cache.hits   {layer=exact|semantic}
 *   nexusai.cache.misses {}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final EmbeddingModel embeddingModel;
    private final MeterRegistry meterRegistry;
    private final SemanticCacheRepository cacheRepository;

    @Value("${nexusai.cache.ttl-seconds:3600}")
    private long ttlSeconds;

    @Value("${nexusai.cache.similarity-threshold:0.92}")
    private double similarityThreshold;

    private static final String REDIS_PREFIX = "nexusai:cache:";

    // ── Look up ───────────────────────────────────────────────────────────────

    public Mono<Optional<CachedResponse>> lookup(String prompt) {
        String key = REDIS_PREFIX + sha256(prompt);

        return redisTemplate.opsForValue().get(key)
                .map(v -> {
                    log.debug("[Cache] Exact hit | key={}", key);
                    incrementHit("exact");
                    return Optional.of(new CachedResponse(v, true, "exact"));
                })
                .switchIfEmpty(
                    semanticLookup(prompt)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .doOnNext(opt -> {
                            if (opt.isEmpty()) incrementMiss();
                        })
                )
                .onErrorResume(ex -> {
                    log.warn("[Cache] Lookup error: {}", ex.getMessage());
                    return Mono.just(Optional.empty());
                });
    }

    // ── Store ─────────────────────────────────────────────────────────────────

    public Mono<Void> store(String prompt, String response) {
        String key = REDIS_PREFIX + sha256(prompt);

        Mono<Void> redisStore = redisTemplate.opsForValue()
                .set(key, response, Duration.ofSeconds(ttlSeconds))
                .then();

        Mono<Void> vectorStore = Mono.fromCallable(() -> {
            float[] embedding = embeddingModel.embed(prompt);
            cacheRepository.save(new SemanticCacheEntry(prompt, response, embedding));
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic()).then();

        return Mono.when(redisStore, vectorStore)
                .doOnSuccess(v -> log.debug("[Cache] Stored entry for prompt hash={}", sha256(prompt).substring(0, 8)))
                .onErrorResume(ex -> {
                    log.warn("[Cache] Store error: {}", ex.getMessage());
                    return Mono.empty();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Mono<CachedResponse> semanticLookup(String prompt) {
        return Mono.fromCallable(() -> {
            float[] embedding = embeddingModel.embed(prompt);
            return cacheRepository.findBySimilarity(embedding, similarityThreshold);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(opt -> {
            if (opt.isPresent()) {
                log.debug("[Cache] Semantic hit");
                incrementHit("semantic");
                return Mono.just(new CachedResponse(opt.get().response(), true, "semantic"));
            }
            return Mono.empty();
        })
        .onErrorResume(ex -> {
            log.warn("[Cache] Semantic lookup error: {}", ex.getMessage());
            return Mono.empty();
        });
    }

    private void incrementHit(String layer) {
        Counter.builder("nexusai.cache.hits").tag("layer", layer)
                .register(meterRegistry).increment();
    }

    private void incrementMiss() {
        Counter.builder("nexusai.cache.misses")
                .register(meterRegistry).increment();
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

}
