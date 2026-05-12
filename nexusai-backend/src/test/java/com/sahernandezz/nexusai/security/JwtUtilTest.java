package com.sahernandezz.nexusai.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil — Unit Tests")
class JwtUtilTest {

    private static final String SECRET =
            "nexusai-super-secret-key-for-testing-minimum-32-characters";
    private static final long ACCESS_EXPIRY_MINUTES = 60L;
    private static final long REFRESH_EXPIRY_DAYS   = 7L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_EXPIRY_MINUTES, REFRESH_EXPIRY_DAYS);
    }

    // ── generateAccessToken ────────────────────────────────────────────────────

    @Test
    @DisplayName("generateAccessToken: token is valid and contains correct claims")
    void shouldGenerateValidAccessToken() {
        String token = jwtUtil.generateAccessToken("admin", List.of("ADMIN", "USER"));

        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.getUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.getRoles(token)).containsExactlyInAnyOrder("ADMIN", "USER");
        assertThat(jwtUtil.isAccessToken(token)).isTrue();
        assertThat(jwtUtil.isRefreshToken(token)).isFalse();
    }

    // ── generateRefreshToken ───────────────────────────────────────────────────

    @Test
    @DisplayName("generateRefreshToken: token is valid and flagged as refresh")
    void shouldGenerateValidRefreshToken() {
        String token = jwtUtil.generateRefreshToken("user");

        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.getUsername(token)).isEqualTo("user");
        assertThat(jwtUtil.isRefreshToken(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isFalse();
    }

    // ── isValid ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isValid: returns false for a tampered token")
    void shouldReturnFalseForTamperedToken() {
        String token = jwtUtil.generateAccessToken("admin", List.of("ADMIN"));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isValid: returns false for a random string")
    void shouldReturnFalseForGarbageToken() {
        assertThat(jwtUtil.isValid("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("isValid: returns false for blank token")
    void shouldReturnFalseForBlankToken() {
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    // ── getClaims ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getClaims: returns all expected claims from a valid token")
    void shouldReturnClaimsFromValidToken() {
        String token = jwtUtil.generateAccessToken("sergio", List.of("USER"));
        Claims claims = jwtUtil.getClaims(token);

        assertThat(claims.getSubject()).isEqualTo("sergio");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.get("roles", List.class)).contains("USER");
    }

    @Test
    @DisplayName("getClaims: throws for an invalid token")
    void shouldThrowForInvalidTokenWhenGettingClaims() {
        assertThatThrownBy(() -> jwtUtil.getClaims("bad.token.here"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    // ── getRoles ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRoles: returns empty list when no roles are set")
    void shouldReturnEmptyRolesList() {
        String token = jwtUtil.generateAccessToken("user", List.of());

        assertThat(jwtUtil.getRoles(token)).isEmpty();
    }

    // ── different secrets ─────────────────────────────────────────────────────

    @Test
    @DisplayName("isValid: token signed with different secret is invalid")
    void tokenSignedWithDifferentSecretShouldBeInvalid() {
        JwtUtil otherUtil = new JwtUtil(
                "completely-different-secret-key-for-testing-32chars!!",
                ACCESS_EXPIRY_MINUTES,
                REFRESH_EXPIRY_DAYS);

        String foreignToken = otherUtil.generateAccessToken("hacker", List.of("ADMIN"));

        assertThat(jwtUtil.isValid(foreignToken)).isFalse();
    }
}

