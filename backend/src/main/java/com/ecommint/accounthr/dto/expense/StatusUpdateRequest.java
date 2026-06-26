package com.ecommint.accounthr.dto.expense;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * E3-07 — Bir harcama satırının (temsilci invoice'unun) fatura durumunu elle değiştirme
 * isteği. Yalnızca HEDEF durum taşınır; UI rengi/metni İSTEKTEN ALINMAZ — durumdan
 * merkezî olarak ({@code StatusColors}/{@code StatusText}) türetilir ve DB'de tutulmaz.
 *
 * <p>Geçersiz/bilinmeyen enum değeri Spring tarafından gövde deserialize'ında
 * {@code HttpMessageNotReadableException} olarak reddedilir (→ 400); eksik/null değer
 * {@link NotNull} ile {@code MethodArgumentNotValidException} olur (→ 400 VALIDATION_ERROR).
 */
@Schema(description = "Fatura durumu değiştirme isteği (E3-07). Yalnızca hedef durum; renk/metin sunucuda türetilir.")
public record StatusUpdateRequest(

        @Schema(description = "Yeni fatura durumu.",
                example = "FOUND",
                allowableValues = { "FOUND", "E_INVOICE", "EXPECTED", "TO_INVESTIGATE", "IGNORED" })
        @NotNull(message = "status is required")
        InvoiceStatus status) {
}
