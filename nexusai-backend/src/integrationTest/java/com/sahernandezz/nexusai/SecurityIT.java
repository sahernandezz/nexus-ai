package com.sahernandezz.nexusai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

@DisplayName("Security — Integration Tests")
class SecurityIT extends AbstractIntegrationTest {

    // ─── Helper to obtain a valid access token ──────────────────────────────────
    private String loginAndGetToken(String username, String password) {
        return webTestClient()
                .post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .exchange()
                .expectStatus().isOk()
                .returnResult(Map.class)
                .getResponseBody()
                .blockFirst()
                .get("accessToken")
                .toString();
    }

    // ─── Public endpoints ───────────────────────────────────────────────────────

    @Test
    @DisplayName("/api/health is accessible without token")
    void healthEndpointIsPublic() {
        webTestClient().get().uri("/api/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("/auth/login is accessible without token")
    void loginEndpointIsPublic() {
        // Password is at least 6 chars to clear the @Size validator on
        // LoginRequest — what we want to assert is the security layer letting
        // the request through (401 from bad credentials), NOT a 400 short-
        // circuit from the validation handler before security even runs.
        webTestClient().post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "admin", "password", "wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized(); // 401 from bad credentials, not from security
    }

    @Test
    @DisplayName("/swagger-ui.html is accessible without token")
    void swaggerUiIsPublic() {
        webTestClient().get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection(); // redirects to /swagger-ui/index.html
    }

    // ─── Protected endpoints ────────────────────────────────────────────────────

    @Test
    @DisplayName("Protected endpoint without token returns 401")
    void protectedEndpointWithoutTokenReturns401() {
        webTestClient().get().uri("/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected endpoint with valid token returns 200")
    void protectedEndpointWithValidTokenReturns200() {
        String token = loginAndGetToken("admin", "admin123");

        webTestClient().get().uri("/auth/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("admin");
    }

    @Test
    @DisplayName("Protected endpoint with tampered token returns 401")
    void protectedEndpointWithTamperedTokenReturns401() {
        String token = loginAndGetToken("admin", "admin123");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        webTestClient().get().uri("/auth/me")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Admin-only endpoint returns 403 for USER role")
    void adminEndpointForbiddenForUserRole() {
        String token = loginAndGetToken("user", "user123");

        webTestClient().get().uri("/api/admin/test")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }
}

