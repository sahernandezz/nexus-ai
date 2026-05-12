package com.sahernandezz.nexusai.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ChatRouter {

    @Bean
    public RouterFunction<ServerResponse> chatRoutes(ChatHandler handler) {
        return RouterFunctions.route()
                .POST("/api/chat/stream",     handler::stream)
                .GET("/api/chat/models",      handler::models)
                .build();
    }
}

