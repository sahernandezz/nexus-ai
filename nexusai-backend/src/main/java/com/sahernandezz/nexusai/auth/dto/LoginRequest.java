package com.sahernandezz.nexusai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 6, message = "password must be at least 6 characters")
        String password
) {}

