package com.ecommint.accounthr.dto.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceSource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code Service} entity'sinin temiz dış sözleşmesi (E1-07 referans DTO).
 *
 * <p>Entity SIZDIRMAZ: lazy ilişkiler (provider/defaultCard/usingTeam) DTO'da
 * düz alanlara indirgenir; böylece JSON serileştirmede lazy-proxy/döngü riski
 * yoktur. Para {@link BigDecimal} (decimal), tarihler ISO-8601 olarak verilir.
 */
@Schema(description = "Servis (abonelik) — master liste kaydının dış gösterimi")
public record ServiceDto(
        @Schema(example = "1") Long id,
        @Schema(example = "Claude AI") String name,
        @Schema(description = "Fatura kesen firma adı", example = "Anthropic", nullable = true)
        String providerName,
        @Schema(description = "Varsayılan kartın son 4 hanesi", example = "3800", nullable = true)
        String defaultCardLastFour,
        @Schema(description = "Servisi kullanan takım adı", example = "Engineering", nullable = true)
        String usingTeamName,
        @Schema(example = "MONTHLY") Frequency frequency,
        @Schema(example = "YES") ActiveState activeState,
        @Schema(description = "Operasyonel toplama dahil olmayan bilgi-amaçlı kalem mi", example = "false")
        boolean informational,
        @Schema(description = "Yaklaşık aylık TL tutarı", example = "1200.00", nullable = true)
        BigDecimal approxAmountTry,
        @Schema(example = "EMAIL", nullable = true) InvoiceSource invoiceSource,
        @Schema(description = "Kullanım amacı", nullable = true) String purpose,
        @Schema(description = "Özel notlar", nullable = true) String notes,
        @Schema(description = "Oluşturulma zamanı (ISO-8601)") LocalDateTime createdAt,
        @Schema(description = "Son güncelleme zamanı (ISO-8601)") LocalDateTime updatedAt) {
}
