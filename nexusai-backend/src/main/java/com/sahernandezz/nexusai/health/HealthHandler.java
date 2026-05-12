package com.sahernandezz.nexusai.health;

import com.sahernandezz.nexusai.health.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class HealthHandler {

    @Value("${nexusai.version:1.0.0}")
    private String version;

    @Value("${spring.profiles.active:default}")
    private String environment;

    public Mono<ServerResponse> health(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(HealthResponse.up(version, environment));
    }
}

