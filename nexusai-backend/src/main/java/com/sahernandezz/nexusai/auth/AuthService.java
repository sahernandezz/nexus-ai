package com.sahernandezz.nexusai.auth;

import com.sahernandezz.nexusai.auth.dto.LoginRequest;
import com.sahernandezz.nexusai.auth.dto.LoginResponse;
import com.sahernandezz.nexusai.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${security.jwt.access-token-expiration-minutes:60}")
    private long accessExpirationMinutes;

    public Mono<LoginResponse> login(LoginRequest request) {
        UsernamePasswordAuthenticationToken credentials =
                new UsernamePasswordAuthenticationToken(request.username(), request.password());

        return authenticationManager.authenticate(credentials)
                .map(auth -> buildLoginResponse(auth, request.username()))
                .onErrorMap(ex -> new BadCredentialsException("Invalid username or password"));
    }

    public Mono<LoginResponse> refresh(String refreshToken) {
        return Mono.fromCallable(() -> {
            if (!jwtUtil.isValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
                throw new BadCredentialsException("Invalid or expired refresh token");
            }
            String username = jwtUtil.getUsername(refreshToken);
            // In a real app, look up roles from DB here
            List<String> roles = List.of("USER");
            String newAccessToken = jwtUtil.generateAccessToken(username, roles);
            String newRefreshToken = jwtUtil.generateRefreshToken(username);
            return LoginResponse.of(newAccessToken, newRefreshToken, accessExpirationMinutes, username, roles);
        });
    }

    private LoginResponse buildLoginResponse(Authentication auth, String username) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .toList();

        String accessToken = jwtUtil.generateAccessToken(username, roles);
        String refreshToken = jwtUtil.generateRefreshToken(username);
        return LoginResponse.of(accessToken, refreshToken, accessExpirationMinutes, username, roles);
    }
}

