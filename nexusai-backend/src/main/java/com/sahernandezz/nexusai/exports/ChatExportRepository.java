package com.sahernandezz.nexusai.exports;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChatExportRepository extends ReactiveCrudRepository<ChatExportEntity, UUID> {
    Mono<ChatExportEntity> findByIdAndUserId(UUID id, UUID userId);
}
