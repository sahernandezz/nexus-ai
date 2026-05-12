package com.sahernandezz.nexusai.conversations.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationDto(
        UUID id,
        String title,
        String model,
        Instant createdAt,
        Instant updatedAt,
        List<MessageDto> messages
) {}
