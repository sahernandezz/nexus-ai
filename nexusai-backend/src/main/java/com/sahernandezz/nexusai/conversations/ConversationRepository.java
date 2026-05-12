package com.sahernandezz.nexusai.conversations;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConversationRepository extends ReactiveCrudRepository<ConversationEntity, UUID> {

    @Query("SELECT * FROM conversations WHERE user_id = :userId ORDER BY updated_at DESC")
    Flux<ConversationEntity> findAllByUserId(UUID userId);

    Mono<ConversationEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("DELETE FROM conversations WHERE id = :id AND user_id = :userId")
    Mono<Void> deleteByIdAndUserId(UUID id, UUID userId);
}
