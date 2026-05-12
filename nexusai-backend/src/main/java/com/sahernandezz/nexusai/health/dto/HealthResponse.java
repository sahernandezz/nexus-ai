package com.sahernandezz.nexusai.health.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record HealthResponse(
        String status,
        String version,
        String environment,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {
    public static HealthResponse up(String version, String environment) {
        return new HealthResponse("UP", version, environment, Instant.now());
    }
}

