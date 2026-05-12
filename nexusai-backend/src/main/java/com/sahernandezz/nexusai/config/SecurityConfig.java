package com.sahernandezz.nexusai.config;

import com.sahernandezz.nexusai.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/api/health",
                                "/auth/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/actuator/health",
                                "/actuator/prometheus"
                        ).permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyExchange().hasAnyRole("ADMIN", "USER", "AGENT")
                )
                .exceptionHandling(spec -> spec
                        .authenticationEntryPoint(jsonAuthEntryPoint())
                        .accessDeniedHandler(jsonAccessDeniedHandler()))
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * Returns a clean 401 JSON response with NO {@code WWW-Authenticate: Basic}
     * header. The default Spring Security entry point sets that header even
     * when http-basic auth is disabled, which makes browsers (Chrome, Edge,
     * etc.) pop up the native Basic-auth dialog on XHR/fetch 401s — bad UX
     * for a JWT SPA where the frontend should silently redirect to /login.
     */
    private static ServerAuthenticationEntryPoint jsonAuthEntryPoint() {
        return (exchange, ex) -> writeJsonStatus(exchange, HttpStatus.UNAUTHORIZED,
                "{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}");
    }

    private static ServerAccessDeniedHandler jsonAccessDeniedHandler() {
        return (exchange, ex) -> writeJsonStatus(exchange, HttpStatus.FORBIDDEN,
                "{\"error\":\"forbidden\",\"message\":\"Access denied\"}");
    }

    private static Mono<Void> writeJsonStatus(
            org.springframework.web.server.ServerWebExchange exchange,
            HttpStatus status, String body) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Belt-and-suspenders: ensure no WWW-Authenticate sneaks in via downstream filters.
        response.getHeaders().remove(HttpHeaders.WWW_AUTHENTICATE);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * In-memory user store for development.
     * Replace with a database-backed ReactiveUserDetailsService for production.
     */
    @Bean
    public ReactiveUserDetailsService reactiveUserDetailsService(PasswordEncoder encoder) {
        var admin = User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN", "USER")
                .build();

        var user = User.withUsername("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build();

        var agent = User.withUsername("agent")
                .password(encoder.encode("agent123"))
                .roles("AGENT")
                .build();

        return new MapReactiveUserDetailsService(admin, user, agent);
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(
            ReactiveUserDetailsService userDetailsService,
            PasswordEncoder encoder) {
        var manager = new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        manager.setPasswordEncoder(encoder);
        return manager;
    }
}

