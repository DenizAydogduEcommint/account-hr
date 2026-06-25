package com.ecommint.accounthr.dto.auth;

/**
 * POST /api/auth/login ve /api/auth/refresh başarılı yanıtı.
 * tokenType sabittir ("Bearer"), expiresIn access token TTL'i (saniye).
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
