package com.ecommint.accounthr.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/refresh ve /api/auth/logout istek gövdesi. */
public record RefreshRequest(
        @NotBlank String refreshToken) {
}
