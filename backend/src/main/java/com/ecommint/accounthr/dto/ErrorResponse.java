package com.ecommint.accounthr.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Projenin tek standart hata yanıtı (E1-07).
 *
 * <p>Hem {@code @RestControllerAdvice} (GlobalExceptionHandler) hem de güvenlik
 * filtre zincirindeki 401/403 işleyicileri (RestAuthenticationEntryPoint /
 * RestAccessDeniedHandler) AYNI bu şekli üretir. Frontend tek bir tipte hata
 * okuyabilsin diye alanlar sabittir.
 *
 * <p>{@code message} İngilizce (koddaki mesaj); kullanıcıya gösterilen Türkçe
 * metni frontend yerelleştirir. {@code traceId} log korelasyonu içindir (E1-05,
 * MDC {@code correlationId}); yoksa {@code null} olabilir.
 */
@Schema(description = "Standart hata yanıtı. Tüm hatalar (validation/auth/not-found/server) bu şekli kullanır.")
public record ErrorResponse(
        @Schema(description = "Hatanın oluştuğu an (ISO-8601 instant)", example = "2026-06-25T08:30:00Z")
        Instant timestamp,

        @Schema(description = "HTTP durum kodu", example = "400")
        int status,

        @Schema(description = "Kısa hata kodu (makine-okur)", example = "VALIDATION_ERROR")
        String error,

        @Schema(description = "İnsan-okur açıklama (İngilizce; frontend Türkçeye çevirir)",
                example = "Validation failed for one or more fields.")
        String message,

        @Schema(description = "Hatanın oluştuğu istek yolu", example = "/api/v1/services")
        String path,

        @Schema(description = "Log korelasyon kimliği (MDC correlationId); olmayabilir",
                example = "3f1a9c2e-7b4d-4e2a-9c1f-0a2b3c4d5e6f", nullable = true)
        String traceId,

        @Schema(description = "Yalnızca validation hatalarında alan bazlı hata listesi", nullable = true)
        List<FieldError> fieldErrors) {

    /** Alan bazlı validation hatası. */
    @Schema(description = "Tek bir alanın validation hatası")
    public record FieldError(
            @Schema(description = "Hatalı alan adı", example = "email")
            String field,
            @Schema(description = "Alan hata mesajı (İngilizce)", example = "must not be blank")
            String message) {
    }
}
