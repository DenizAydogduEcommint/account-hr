package com.ecommint.accounthr.dto.admin;

import com.ecommint.accounthr.domain.enums.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin kullanıcı oluşturma isteği (E1-08, POST /api/v1/admin/users).
 *
 * <p>{@code role} enum tipinde olduğundan geçersiz bir rol değeri ("GARBAGE")
 * deserialize sırasında {@code HttpMessageNotReadableException}'a yol açar →
 * {@code GlobalExceptionHandler} 400 VALIDATION_ERROR döner.
 *
 * <p>{@code password} en az 8 karakter; servis katmanında BCrypt ile hash'lenir,
 * plaintext asla saklanmaz.
 */
public record CreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotNull UserRole role,
        @NotBlank @Size(min = 8, message = "Parola en az 8 karakter olmalı.") String password) {
}
