package com.sahernandezz.nexusai.conversations;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MessageRepository extends ReactiveCrudRepository<MessageEntity, UUID> {

    @Query("""
            SELECT * FROM chat_messages
            WHERE conversation_id = :conversationId AND client_id IS NOT NULL
            ORDER BY message_index NULLS LAST, created_at
            """)
    Flux<MessageEntity> findByConversationOrderedFrontend(UUID conversationId);

    Mono<MessageEntity> findByClientId(UUID clientId);

    @Query("DELETE FROM chat_messages WHERE client_id = :clientId AND conversation_id = :conversationId")
    Mono<Void> deleteByClientId(UUID conversationId, UUID clientId);
}
