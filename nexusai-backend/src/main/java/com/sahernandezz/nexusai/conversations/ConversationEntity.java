package com.sahernandezz.nexusai.conversations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("conversations")
public record ConversationEntity(
                @Id UUID id,
                @Column("user_id") UUID userId,
                String title,
                String model,
                @Column("created_at") Instant createdAt,
                @Column("updated_at") Instant updatedAt,
                @Transient boolean isNew) implements Persistable<UUID> {

        @PersistenceCreator
        public ConversationEntity(UUID id, UUID userId, String title, String model,
                        Instant createdAt, Instant updatedAt) {
                this(id, userId, title, model, createdAt, updatedAt, false);
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
