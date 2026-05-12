package com.sahernandezz.nexusai.stats;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated usage statistics for the dashboard.
 *
 * Pulls from two sources:
 *   - DB: per-user counts (conversations, messages, attachments, exports, RAG docs)
 *   - Micrometer registry: cache hits/misses, LLM call counts, queue depth
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final DatabaseClient db;
    private final MeterRegistry meterRegistry;

    public Mono<Map<String, Object>> overview(UUID userId) {
        Mono<Long> convs       = countOwned("conversations", userId);
        Mono<Long> msgs        = countMessages(userId);
        Mono<Long> attachments = countOwned("chat_attachments", userId);
        Mono<Long> exports     = countOwned("chat_exports", userId);
        Mono<Long> ragDocs     = countOwnedDocuments(userId);
        Mono<List<DailyCount>> daily = dailyCounts(userId);

        return Mono.zip(convs, msgs, attachments, exports, ragDocs, daily)
                .map(tuple -> {
                    Map<String, Object> result = new LinkedHashMap<>();

                    Map<String, Object> usage = new LinkedHashMap<>();
                    usage.put("conversations", tuple.getT1());
                    usage.put("messages",      tuple.getT2());
                    usage.put("attachments",   tuple.getT3());
                    usage.put("exports",       tuple.getT4());
                    usage.put("ragDocuments",  tuple.getT5());
                    result.put("usage", usage);

                    Map<String, Object> cache = new LinkedHashMap<>();
                    cache.put("hitsExact",    counterValue("nexusai.cache.hits", "layer", "exact"));
                    cache.put("hitsSemantic", counterValue("nexusai.cache.hits", "layer", "semantic"));
                    cache.put("misses",       counterValue("nexusai.cache.misses", null, null));
                    long totalCacheLookups = (long) cache.get("hitsExact")
                            + (long) cache.get("hitsSemantic")
                            + (long) cache.get("misses");
                    cache.put("hitRate", totalCacheLookups == 0 ? 0.0
                            : Math.round(((double)((long) cache.get("hitsExact") + (long) cache.get("hitsSemantic"))
                                    / totalCacheLookups) * 1000.0) / 10.0);
                    result.put("cache", cache);

                    Map<String, Object> llm = new LinkedHashMap<>();
                    llm.put("requests", counterValue("nexusai.llm.requests", null, null));
                    llm.put("tokensPrompt", counterValue("nexusai.llm.tokens", "type", "prompt"));
                    llm.put("tokensCompletion", counterValue("nexusai.llm.tokens", "type", "completion"));
                    result.put("llm", llm);

                    Map<String, Object> queues = new LinkedHashMap<>();
                    queues.put("documentIngest", gaugeValue("nexusai.rabbitmq.queue.messages", "queue", "nexusai.document.ingest"));
                    queues.put("exportJob",      gaugeValue("nexusai.rabbitmq.queue.messages", "queue", "nexusai.export.job"));
                    queues.put("dlqDocument",    gaugeValue("nexusai.rabbitmq.queue.messages", "queue", "nexusai.dlq.document"));
                    queues.put("dlqEmbedding",   gaugeValue("nexusai.rabbitmq.queue.messages", "queue", "nexusai.dlq.embedding"));
                    result.put("queues", queues);

                    result.put("daily", tuple.getT6());
                    result.put("generatedAt", Instant.now().toString());
                    return result;
                });
    }

    // ── DB queries ───────────────────────────────────────────────────────────

    private Mono<Long> countOwned(String table, UUID userId) {
        return db.sql("SELECT COUNT(*) AS c FROM " + table + " WHERE user_id = :u")
                .bind("u", userId)
                .map(row -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private Mono<Long> countMessages(UUID userId) {
        return db.sql("""
                SELECT COUNT(*) AS c
                FROM chat_messages m
                JOIN conversations c ON c.id = m.conversation_id
                WHERE c.user_id = :u AND m.client_id IS NOT NULL
                """)
                .bind("u", userId)
                .map(row -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private Mono<Long> countOwnedDocuments(UUID userId) {
        // documents table has user_id from V1; safe.
        return db.sql("SELECT COUNT(*) AS c FROM documents WHERE user_id = :u")
                .bind("u", userId)
                .map(row -> row.get("c", Long.class))
                .one()
                .defaultIfEmpty(0L)
                .onErrorReturn(0L);
    }

    /**
     * Last-7-day message counts grouped by day. Returns list of {day, count}.
     */
    private Mono<List<DailyCount>> dailyCounts(UUID userId) {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        return db.sql("""
                SELECT date_trunc('day', m.created_at) AS day, COUNT(*) AS c
                FROM chat_messages m
                JOIN conversations c ON c.id = m.conversation_id
                WHERE c.user_id = :u AND m.client_id IS NOT NULL AND m.created_at >= :since
                GROUP BY day
                ORDER BY day
                """)
                .bind("u", userId)
                .bind("since", since)
                .map(row -> new DailyCount(
                        row.get("day", Instant.class).toString(),
                        row.get("c", Long.class)))
                .all()
                .collectList();
    }

    // ── Micrometer reads ─────────────────────────────────────────────────────

    private long counterValue(String name, String tagKey, String tagVal) {
        Search s = meterRegistry.find(name);
        if (tagKey != null) s = s.tag(tagKey, tagVal);
        return (long) s.counters().stream().mapToDouble(c -> c.count()).sum();
    }

    private long gaugeValue(String name, String tagKey, String tagVal) {
        Search s = meterRegistry.find(name);
        if (tagKey != null) s = s.tag(tagKey, tagVal);
        return (long) s.gauges().stream().mapToDouble(g -> g.value()).sum();
    }

    public record DailyCount(String day, long count) {}
}
