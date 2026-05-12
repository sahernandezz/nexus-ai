package com.sahernandezz.nexusai.exports;

import com.sahernandezz.nexusai.config.RabbitMQConfig;
import com.sahernandezz.nexusai.conversations.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Public-facing operations for chat exports — request creation, status lookup,
 * and worker-side state transitions. The actual zip-build + MinIO upload runs
 * in {@link ChatExportConsumer} off a RabbitMQ queue so big exports don't
 * block the request thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatExportService {

    private final ChatExportRepository repo;
    private final ConversationRepository convRepo;
    private final RabbitTemplate rabbitTemplate;

    public Mono<ChatExportEntity> request(UUID userId, UUID conversationId) {
        Mono<Void> validation = conversationId == null ? Mono.empty()
                : convRepo.findByIdAndUserId(conversationId, userId)
                        .switchIfEmpty(Mono.error(new IllegalStateException("conversation not found")))
                        .then();

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ChatExportEntity entity = new ChatExportEntity(
                id, userId, conversationId, "PENDING", null, null, now, now, true);
        return validation
                .then(repo.save(entity))
                .doOnNext(saved -> {
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXPORT_EXCHANGE,
                            RabbitMQConfig.EXPORT_JOB_KEY,
                            new ExportJobEvent(saved.id(), userId, conversationId));
                    log.info("[Export] Enqueued exportId={} userId={} convId={}",
                            saved.id(), userId, conversationId);
                });
    }

    public Mono<ChatExportEntity> findOwned(UUID id, UUID userId) {
        return repo.findByIdAndUserId(id, userId);
    }

    Mono<ChatExportEntity> markProcessing(UUID id) {
        return repo.findById(id).flatMap(e -> repo.save(new ChatExportEntity(
                e.id(), e.userId(), e.conversationId(), "PROCESSING",
                e.attachmentId(), null, e.createdAt(), Instant.now(), false)));
    }

    Mono<ChatExportEntity> markReady(UUID id, UUID attachmentId) {
        return repo.findById(id).flatMap(e -> repo.save(new ChatExportEntity(
                e.id(), e.userId(), e.conversationId(), "READY",
                attachmentId, null, e.createdAt(), Instant.now(), false)));
    }

    Mono<ChatExportEntity> markFailed(UUID id, String message) {
        return repo.findById(id).flatMap(e -> repo.save(new ChatExportEntity(
                e.id(), e.userId(), e.conversationId(), "FAILED",
                e.attachmentId(), message, e.createdAt(), Instant.now(), false)));
    }
}
