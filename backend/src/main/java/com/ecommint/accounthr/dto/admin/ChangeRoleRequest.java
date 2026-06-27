package com.ecommint.accounthr.dto.admin;

import com.ecommint.accounthr.domain.enums.UserRole;

import jakarta.validation.constraints.NotNull;

/**
 * Admin rol değiştirme isteği (E1-08, PATCH /api/v1/admin/users/{id}/role).
 *
 * <p>{@code role} enum tipinde; geçersiz değer 400 döner. Son aktif ADMIN'in
 * rolünü düşürmek 409 LAST_ADMIN ile reddedilir (bkz. AdminUserService).
 */
public record ChangeRoleRequest(
        @NotNull UserRole role) {
}
