package com.sahernandezz.nexusai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GET /api/health — Integration Test")
class HealthEndpointIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Should return 200 and status UP without authentication")
    void shouldReturnHealthUp() {
        webTestClient()
                .get().uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.version").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    @DisplayName("Should return JSON content type")
    void shouldReturnJsonContentType() {
        webTestClient()
                .get().uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json");
    }
}

