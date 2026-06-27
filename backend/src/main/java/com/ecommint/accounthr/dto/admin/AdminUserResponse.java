package com.ecommint.accounthr.dto.admin;

import java.time.LocalDateTime;

import com.ecommint.accounthr.domain.AppUser;

/**
 * Admin kullanıcı yönetimi (E1-08) yanıt DTO'su.
 *
 * <p><b>GÜVENLİK:</b> {@code passwordHash} ASLA bu yanıta dahil edilmez. BCrypt
 * parola hash'i hiçbir HTTP yanıtında dönmez; bu kayıt yalnızca güvenli alanları
 * (id, email, fullName, role, active, teamId, createdAt) açar.
 *
 * <p>{@code role} düz string olarak döner: "ADMIN" | "ACCOUNTING" | "TEAM_MEMBER".
 * {@code teamId} kullanıcının takımı yoksa {@code null}'dur.
 */
public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        boolean active,
        Long teamId,
        LocalDateTime createdAt) {

    public static AdminUserResponse from(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive(),
                user.getTeam() == null ? null : user.getTeam().getId(),
                user.getCreatedAt());
    }
}
