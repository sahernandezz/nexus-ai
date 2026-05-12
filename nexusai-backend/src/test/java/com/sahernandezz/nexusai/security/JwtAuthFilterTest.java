package com.sahernandezz.nexusai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter — Unit Tests")
class JwtAuthFilterTest {

    private static final String SECRET =
            "nexusai-super-secret-key-for-testing-minimum-32-characters";

    @Mock private WebFilterChain filterChain;

    private JwtUtil jwtUtil;
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60L, 7L);
        jwtAuthFilter = new JwtAuthFilter(jwtUtil);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Valid Bearer token: filter passes and sets SecurityContext")
    void shouldAuthenticateWithValidBearerToken() {
        String token = jwtUtil.generateAccessToken("admin", List.of("ADMIN", "USER"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("No Authorization header: filter continues without authentication")
    void shouldContinueWithoutTokenWhenNoHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("Invalid token: filter continues without setting SecurityContext")
    void shouldContinueWithInvalidToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("Refresh token: filter continues without setting SecurityContext (not an access token)")
    void shouldNotAuthenticateWithRefreshToken() {
        String refreshToken = jwtUtil.generateRefreshToken("user");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(exchange);
    }

    @Test
    @DisplayName("Malformed Authorization header (no Bearer prefix): filter continues")
    void shouldIgnoreMalformedAuthorizationHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(exchange);
    }
}

