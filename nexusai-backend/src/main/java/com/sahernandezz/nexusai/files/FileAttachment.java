package com.sahernandezz.nexusai.files;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity for {@code chat_attachments} — every file the user uploads
 * (images, PDFs not RAG-indexed, exported zips). RAG-indexed PDFs go through
 * the {@code documents} table and are served from
 * {@code /api/rag/documents/{id}/file}.
 *
 * Implements {@link Persistable} with a transient {@code isNew} flag so
 * {@code repo.save()} dispatches to INSERT instead of UPDATE when we
 * pre-generate the UUID client-side.
 */
@Table("chat_attachments")
public record FileAttachment(
        @Id UUID id,
        @Column("user_id") UUID userId,
        @Column("conversation_id") UUID conversationId,
        String filename,
        @Column("content_type") String contentType,
        @Column("size_bytes") Long sizeBytes,
        @Column("minio_key") String minioKey,
        String kind,
        @Column("created_at") Instant createdAt,
        @Transient boolean isNew) implements Persistable<UUID> {

    @PersistenceCreator
    public FileAttachment(UUID id, UUID userId, UUID conversationId, String filename,
            String contentType, Long sizeBytes, String minioKey, String kind, Instant createdAt) {
        this(id, userId, conversationId, filename, contentType, sizeBytes, minioKey, kind, createdAt, false);
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
