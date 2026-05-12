package com.sahernandezz.nexusai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

@DisplayName("POST /auth/login — Integration Test")
class AuthEndpointIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("Valid credentials: returns 200 with access and refresh tokens")
    void shouldLoginWithValidCredentials() {
        webTestClient()
                .post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "admin123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.username").isEqualTo("admin")
                .jsonPath("$.roles").isArray();
    }

    @Test
    @DisplayName("Invalid password: returns 401")
    void shouldReturn401ForInvalidPassword() {
        webTestClient()
                .post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Unknown user: returns 401")
    void shouldReturn401ForUnknownUser() {
        webTestClient()
                .post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "nobody", "password", "password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Missing password field: returns 400")
    void shouldReturn400WhenPasswordMissing() {
        webTestClient()
                .post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin"))
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    @DisplayName("Refresh with valid token: returns new token pair")
    void shouldRefreshWithValidRefreshToken() {
        // First login to get a refresh token
        String refreshToken = webTestClient()
                .post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "user", "password", "user123"))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("refreshToken")
                .toString();

        // Then use it to get a new access token
        webTestClient()
                .post().uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("refreshToken", refreshToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty();
    }
}

