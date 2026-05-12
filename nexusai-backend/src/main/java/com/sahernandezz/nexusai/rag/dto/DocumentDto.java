package com.sahernandezz.nexusai.rag.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sahernandezz.nexusai.rag.ingestion.DocumentStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentDto(
        String id,
        String filename,
        String contentType,
        Long sizeBytes,
        DocumentStatus status,
        String errorMsg,
        Instant createdAt,
        Instant updatedAt
) {}

