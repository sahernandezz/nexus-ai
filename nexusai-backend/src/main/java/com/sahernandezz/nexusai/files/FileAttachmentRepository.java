package com.sahernandezz.nexusai.files;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileAttachmentRepository extends ReactiveCrudRepository<FileAttachment, UUID> {
    Mono<FileAttachment> findByIdAndUserId(UUID id, UUID userId);
}
