package com.ecommint.accounthr.dto.admin;

import jakarta.validation.constraints.NotNull;

/**
 * Admin aktiflik değiştirme isteği (E1-08, PATCH /api/v1/admin/users/{id}/active).
 *
 * <p>Son aktif ADMIN'i pasifleştirmek 409 LAST_ADMIN ile reddedilir (bkz.
 * AdminUserService). Sert silme (hard DELETE) YOKTUR; pasifleştirme tek yoldur.
 */
public record ChangeActiveRequest(
        @NotNull Boolean active) {
}
