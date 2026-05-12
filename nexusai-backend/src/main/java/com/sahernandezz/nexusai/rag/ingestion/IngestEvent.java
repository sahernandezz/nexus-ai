package com.sahernandezz.nexusai.rag.ingestion;

import java.io.Serializable;

/**
 * RabbitMQ message published when a document is ready for ingestion.
 */
public record IngestEvent(
        String documentId,
        String filename,
        String storagePath,   // temp file path or object-store key
        String contentType,
        Long sizeBytes
) implements Serializable {}

