package com.sahernandezz.nexusai.health;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HealthRouter {

    @Bean
    @RouterOperation(
            path = "/api/health",
            method = RequestMethod.GET,
            beanClass = HealthHandler.class,
            beanMethod = "health"
    )
    public RouterFunction<ServerResponse> healthRoutes(HealthHandler handler) {
        return RouterFunctions.route()
                .GET("/api/health", handler::health)
                .build();
    }
}

