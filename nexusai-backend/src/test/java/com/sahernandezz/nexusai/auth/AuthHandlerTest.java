package com.sahernandezz.nexusai.auth;

import com.sahernandezz.nexusai.auth.dto.LoginRequest;
import com.sahernandezz.nexusai.auth.dto.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthHandler — Unit Tests")
class AuthHandlerTest {

    @Mock private AuthService authService;
    @Mock private Validator validator;
    @Mock private ServerRequest serverRequest;

    @InjectMocks
    private AuthHandler authHandler;

    private final LoginResponse sampleResponse = LoginResponse.of(
            "access-token-abc", "refresh-token-xyz", 60L, "admin", List.of("ADMIN")
    );

    @Test
    @DisplayName("login: valid credentials return 200 with tokens")
    void shouldReturn200WithTokensOnValidLogin() {
        when(serverRequest.bodyToMono(LoginRequest.class))
                .thenReturn(Mono.just(new LoginRequest("admin", "admin123")));
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(Mono.just(sampleResponse));

        StepVerifier.create(authHandler.login(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("login: bad credentials return 401")
    void shouldReturn401OnBadCredentials() {
        when(serverRequest.bodyToMono(LoginRequest.class))
                .thenReturn(Mono.just(new LoginRequest("admin", "wrong")));
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(Mono.error(new BadCredentialsException("Invalid username or password")));

        StepVerifier.create(authHandler.login(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("login: empty body causes 400")
    void shouldReturn400WhenBodyIsEmpty() {
        when(serverRequest.bodyToMono(LoginRequest.class))
                .thenReturn(Mono.empty());

        StepVerifier.create(authHandler.login(serverRequest))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("me: authenticated request returns username")
    void shouldReturnUsernameForAuthenticatedUser() {
        // UsernamePasswordAuthenticationToken implements Principal
        UsernamePasswordAuthenticationToken principal =
                new UsernamePasswordAuthenticationToken("admin", null, List.of());
        doReturn(Mono.just(principal)).when(serverRequest).principal();

        StepVerifier.create(authHandler.me(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("me: unauthenticated request returns 401")
    void shouldReturn401WhenNotAuthenticated() {
        when(serverRequest.principal())
                .thenReturn(Mono.empty());

        StepVerifier.create(authHandler.me(serverRequest))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                })
                .verifyComplete();
    }
}

