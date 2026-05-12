package com.sahernandezz.nexusai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound chat request.
 *
 * @param message      user text
 * @param sessionId    conversation / memory session id (optional – used from M4)
 * @param provider     model provider: "openai" | "ollama" | "gemini"  (default: openai)
 * @param model        override the specific model name, e.g. gpt-4o or llama3.2
 * @param temperature  sampling temperature 0.0–2.0 (optional)
 * @param stream       whether the caller intends to read an SSE stream (informational)
 */
public record ChatRequest(
        @NotBlank(message = "message must not be blank")
        @Size(max = 32_000, message = "message exceeds maximum length")
        String message,

        String sessionId,

        String provider,

        String model,

        Double temperature,

        Boolean stream
) {
    /** Returns the provider, defaulting to "openai". */
    public String resolvedProvider() {
        return (provider == null || provider.isBlank()) ? "openai" : provider.toLowerCase();
    }
}

