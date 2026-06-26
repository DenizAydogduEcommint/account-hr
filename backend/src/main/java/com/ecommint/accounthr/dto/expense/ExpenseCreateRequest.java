package com.ecommint.accounthr.dto.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ecommint.accounthr.domain.enums.Currency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * E3-06 — Elle (manuel) harcama satırı oluşturma isteği. Banka ekstresinden ÖNCE veya
 * ekstre olmadan, kimliği doğrulanmış bir kullanıcının tek bir satır girmesini sağlar.
 *
 * <p>Servis-tabanlı: satır {@code serviceId} ile bir servise bağlanır; sağlayıcı servisten
 * türetilir. Oluşan satır {@code source=MANUAL} olarak işaretlenir ve varsayılan durumu
 * "Bekleniyor" (EXPECTED) olan bir taslak invoice ile gelir.
 *
 * <p>Alan-bazı (Bean Validation) doğrulama hataları {@code GlobalExceptionHandler} üzerinden
 * 400 {@code VALIDATION_ERROR} döner.
 */
@Schema(description = "Elle harcama satırı oluşturma isteği (E3-06).")
public record ExpenseCreateRequest(

        @Schema(description = "Satırın bağlanacağı servisin id'si (ZORUNLU).", example = "42")
        @NotNull(message = "serviceId zorunludur.")
        Long serviceId,

        @Schema(description = "İşlem tarihi (ISO YYYY-MM-DD, ZORUNLU). Period bu tarihin "
                + "ayından find-or-create edilir.", example = "2026-03-15")
        @NotNull(message = "transactionDate zorunludur (ISO YYYY-MM-DD).")
        LocalDate transactionDate,

        @Schema(description = "Orijinal (döviz) tutar — pozitif olmalı.", example = "20.00")
        @NotNull(message = "amount zorunludur.")
        @Positive(message = "amount pozitif olmalıdır.")
        BigDecimal amount,

        @Schema(description = "Para birimi (ZORUNLU).", example = "USD")
        @NotNull(message = "currency zorunludur.")
        Currency currency,

        @Schema(description = "Kart ekstresindeki TL karşılığı — pozitif olmalı.", example = "680.50")
        @NotNull(message = "amountTry zorunludur.")
        @Positive(message = "amountTry pozitif olmalıdır.")
        BigDecimal amountTry,

        @Schema(description = "Kartın son 4 hanesi (OPSİYONEL). Verilirse bilinen bir kartla "
                + "eşleşmeli; verilmezse servisin varsayılan kartına düşülür.",
                example = "3800", nullable = true)
        String cardLast4,

        @Schema(description = "Kullanan takımın id'si (OPSİYONEL). Verilirse var olan bir takım "
                + "olmalı.", example = "3", nullable = true)
        Long usingTeamId,

        @Schema(description = "Amaç / kısa açıklama (OPSİYONEL).", example = "LLM API", nullable = true)
        String purpose,

        @Schema(description = "Bilgi-amaçlı satır mı? true ise operasyonel TOPLAM'a dahil "
                + "edilmez (Multinet/sigorta gibi). Varsayılan false.", example = "false")
        boolean informational) {
}
