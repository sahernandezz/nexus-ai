package com.sahernandezz.nexusai.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M8 — Custom Prometheus metrics.
 *
 * Exposes:
 *   nexusai.rabbitmq.queue.messages  {queue}  — messages ready in queue
 *   nexusai.llm.requests             {provider, outcome}   (from MetricsAdvisor)
 *   nexusai.llm.tokens               {provider, type}      (from MetricsAdvisor)
 *   nexusai.llm.latency              {provider}            (from MetricsAdvisor)
 *   nexusai.cache.hits               {layer}               (from SemanticCacheService)
 *   nexusai.cache.misses                                   (from SemanticCacheService)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusAiMetrics {

    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private AmqpAdmin amqpAdmin;

    private static final List<String> WATCHED_QUEUES = List.of(
            "nexusai.document.ingest",
            "nexusai.embedding.process",
            "nexusai.export.job",
            "nexusai.dlq.document",
            "nexusai.dlq.embedding"
    );

    // ── RabbitMQ queue depth gauges ────────────────────────────────────────────

    @Scheduled(fixedDelay = 15_000)
    public void updateQueueMetrics() {
        if (amqpAdmin == null) return;
        for (String queueName : WATCHED_QUEUES) {
            try {
                QueueInformation info = amqpAdmin.getQueueInfo(queueName);
                if (info != null) {
                    Gauge.builder("nexusai.rabbitmq.queue.messages",
                                    info, QueueInformation::getMessageCount)
                            .tag("queue", queueName)
                            .description("Number of messages ready in the queue")
                            .register(meterRegistry);
                }
            } catch (Exception e) {
                log.debug("[Metrics] Could not read queue {}: {}", queueName, e.getMessage());
            }
        }
    }
}

