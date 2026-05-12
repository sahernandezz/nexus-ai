package com.sahernandezz.nexusai.exports;

import java.util.UUID;

/** RabbitMQ payload published when the user requests a chat export. */
public record ExportJobEvent(
        UUID exportId,
        UUID userId,
        UUID conversationId  // null = all conversations
) {}
