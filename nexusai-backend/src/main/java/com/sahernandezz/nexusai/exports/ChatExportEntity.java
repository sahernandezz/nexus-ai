package com.sahernandezz.nexusai.exports;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("chat_exports")
public record ChatExportEntity(
        @Id UUID id,
        @Column("user_id") UUID userId,
        @Column("conversation_id") UUID conversationId, // null = export all conversations
        String status, // PENDING | PROCESSING | READY | FAILED
        @Column("attachment_id") UUID attachmentId,
        @Column("error_msg") String errorMsg,
        @Column("created_at") Instant createdAt,
        @Column("updated_at") Instant updatedAt,
        @Transient boolean isNew) implements Persistable<UUID> {

    @PersistenceCreator
    public ChatExportEntity(UUID id, UUID userId, UUID conversationId, String status,
            UUID attachmentId, String errorMsg, Instant createdAt, Instant updatedAt) {
        this(id, userId, conversationId, status, attachmentId, errorMsg, createdAt, updatedAt, false);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return isNew;
    }
}
