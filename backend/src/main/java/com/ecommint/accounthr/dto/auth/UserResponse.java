package com.ecommint.accounthr.dto.auth;

import com.ecommint.accounthr.domain.AppUser;

/**
 * Kullanıcı bilgisi (login/refresh yanıtının "user" alanı ve /api/auth/me gövdesi).
 * role düz string olarak döner: "ADMIN" | "ACCOUNTING" | "TEAM_MEMBER".
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name());
    }
}
