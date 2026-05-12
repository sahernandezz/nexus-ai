package com.sahernandezz.nexusai.stats;

import com.sahernandezz.nexusai.auth.UserDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StatsHandler {

    private final StatsService stats;
    private final UserDirectory userDirectory;

    /** GET /api/stats — usage overview for the dashboard. */
    public Mono<ServerResponse> overview(ServerRequest req) {
        return userDirectory.currentUserId()
                .flatMap(stats::overview)
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(data));
    }
}

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class StatsRouter {
    @Bean
    RouterFunction<ServerResponse> statsRoutes(StatsHandler handler) {
        return RouterFunctions.route()
                .GET("/api/stats", handler::overview)
                .build();
    }
}
