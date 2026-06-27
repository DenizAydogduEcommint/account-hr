package com.ecommint.accounthr.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin parola değiştirme isteği (E1-08, PATCH /api/v1/admin/users/{id}/password).
 *
 * <p>Yeni parola en az 8 karakter; BCrypt ile yeniden hash'lenir ve hedef kullanıcının
 * tüm aktif refresh token'ları iptal edilir (eski oturumlar geçersizleşir).
 */
public record ChangePasswordRequest(
        @NotBlank @Size(min = 8, message = "Parola en az 8 karakter olmalı.") String password) {
}
