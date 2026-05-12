package com.sahernandezz.nexusai.conversations;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent chat message — also used by Spring AI's JDBC chat memory.
 *
 * The frontend writes {@code clientId} as the deterministic upsert key so
 * retries don't duplicate messages.
 */
@Table("chat_messages")
public record MessageEntity(
        @Id UUID id,
        @Column("conversation_id") UUID conversationId,
        @Column("session_id")      String sessionId,
        String role,
        String content,
        @Column("message_index")   Integer messageIndex,
        @Column("attachments")     Json attachments,    // JSONB array
        @Column("rag_action")      Json ragAction,      // JSONB object
        @Column("cached")          Boolean cached,
        @Column("cache_layer")     String cacheLayer,
        @Column("model")           String model,
        @Column("client_id")       UUID clientId,
        @Column("created_at")      Instant createdAt
) {}
