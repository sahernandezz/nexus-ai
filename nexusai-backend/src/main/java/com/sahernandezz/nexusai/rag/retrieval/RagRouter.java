package com.sahernandezz.nexusai.rag.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class RagRouter {

    @Bean
    public RouterFunction<ServerResponse> ragRoutes(RagHandler handler) {
        return RouterFunctions.route()
                .POST("/api/rag/upload",                  handler::upload)
                .GET("/api/rag/documents",                 handler::list)
                .GET("/api/rag/documents/{id}",            handler::status)
                .GET("/api/rag/documents/{id}/file",       handler::getFile)
                .DELETE("/api/rag/documents/{id}",         handler::delete)
                .POST("/api/rag/stream",                   handler::stream)
                .build();
    }
}

