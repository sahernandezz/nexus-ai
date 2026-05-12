package com.sahernandezz.nexusai.auth;

import com.sahernandezz.nexusai.auth.dto.LoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthHandler {

    private final AuthService authService;
    private final Validator validator;

    public Mono<ServerResponse> login(ServerRequest request) {
        return request.bodyToMono(LoginRequest.class)
                .flatMap(body -> {
                    var errors = new BeanPropertyBindingResult(body, "loginRequest");
                    validator.validate(body, errors);
                    if (errors.hasErrors()) {
                        String msg = errors.getAllErrors().get(0).getDefaultMessage();
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, msg));
                    }
                    return authService.login(body);
                })
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(BadCredentialsException.class, ex ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("error", ex.getMessage())))
                .onErrorResume(ResponseStatusException.class, ex ->
                        ServerResponse.status(ex.getStatusCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("error", ex.getReason())));
    }

    public Mono<ServerResponse> refresh(ServerRequest request) {
        return request.bodyToMono(Map.class)
                .flatMap(body -> {
                    String token = (String) body.get("refreshToken");
                    if (token == null || token.isBlank()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "refreshToken is required"));
                    }
                    return authService.refresh(token);
                })
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(BadCredentialsException.class, ex ->
                        ServerResponse.status(HttpStatus.UNAUTHORIZED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("error", ex.getMessage())))
                .onErrorResume(ResponseStatusException.class, ex ->
                        ServerResponse.status(ex.getStatusCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Map.of("error", ex.getReason())));
    }

    public Mono<ServerResponse> me(ServerRequest request) {
        return request.principal()
                .flatMap(p -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("username", p.getName())))
                .switchIfEmpty(ServerResponse.status(HttpStatus.UNAUTHORIZED).build());
    }
}

