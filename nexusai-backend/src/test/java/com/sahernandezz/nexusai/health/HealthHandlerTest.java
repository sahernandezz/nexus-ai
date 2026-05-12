package com.sahernandezz.nexusai.health;

import com.sahernandezz.nexusai.health.dto.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthHandler — Unit Tests")
class HealthHandlerTest {

    @InjectMocks
    private HealthHandler healthHandler;

    @Test
    @DisplayName("GET /api/health: returns 200 OK")
    void shouldReturnHealth200() {
        ServerRequest request = mock(ServerRequest.class);

        StepVerifier.create(healthHandler.health(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("HealthResponse.up(): status is UP and timestamp is recent")
    void healthResponseUpShouldHaveCorrectFields() {
        Instant before = Instant.now();
        HealthResponse response = HealthResponse.up("1.0.0", "test");
        Instant after = Instant.now();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.version()).isEqualTo("1.0.0");
        assertThat(response.environment()).isEqualTo("test");
        assertThat(response.timestamp()).isAfterOrEqualTo(before);
        assertThat(response.timestamp()).isBeforeOrEqualTo(after);
    }
}

