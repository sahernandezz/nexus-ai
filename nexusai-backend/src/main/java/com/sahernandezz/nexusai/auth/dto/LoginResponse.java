package com.sahernandezz.nexusai.auth.dto;

import java.util.List;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String username,
        List<String> roles
) {
    public static LoginResponse of(String accessToken, String refreshToken,
                                   long expiresInMinutes, String username, List<String> roles) {
        return new LoginResponse(accessToken, refreshToken, "Bearer",
                expiresInMinutes * 60, username, roles);
    }
}

