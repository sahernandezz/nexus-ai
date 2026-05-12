package com.sahernandezz.nexusai.auth;

import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class AuthRouter {

    @Bean
    @RouterOperations({
            @RouterOperation(path = "/auth/login",    method = RequestMethod.POST, beanClass = AuthHandler.class, beanMethod = "login"),
            @RouterOperation(path = "/auth/refresh",  method = RequestMethod.POST, beanClass = AuthHandler.class, beanMethod = "refresh"),
            @RouterOperation(path = "/auth/me",       method = RequestMethod.GET,  beanClass = AuthHandler.class, beanMethod = "me")
    })
    public RouterFunction<ServerResponse> authRoutes(AuthHandler handler) {
        return RouterFunctions.route()
                .POST("/auth/login",   accept(MediaType.APPLICATION_JSON), handler::login)
                .POST("/auth/refresh", accept(MediaType.APPLICATION_JSON), handler::refresh)
                .GET("/auth/me",                                           handler::me)
                .build();
    }
}

