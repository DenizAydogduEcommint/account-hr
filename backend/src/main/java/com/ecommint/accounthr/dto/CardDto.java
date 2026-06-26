package com.ecommint.accounthr.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kart referans verisi (E3-02) — Servisler ekranındaki kart seçimi (dropdown) için.
 *
 * <p>Tanımlı kartlar: Akbank Axess {@code ****3800}, YKB Ticari {@code ****3909},
 * Ziraat Bankkart {@code ****9164}. Salt-okunur referans; entity sızdırmaz.
 */
@Schema(description = "Kart referans kaydı — Servisler ekranı kart seçimi")
public record CardDto(
        @Schema(example = "1") Long id,
        @Schema(description = "Kartın son 4 hanesi", example = "3800") String last4,
        @Schema(description = "Banka adı", example = "Akbank") String bank,
        @Schema(description = "Kart sahibi", example = "Kaan Bingöl", nullable = true) String holder,
        @Schema(description = "Kısa etiket", example = "Akbank Axess", nullable = true) String label) {
}
