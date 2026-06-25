package com.ecommint.accounthr.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/login istek gövdesi. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
