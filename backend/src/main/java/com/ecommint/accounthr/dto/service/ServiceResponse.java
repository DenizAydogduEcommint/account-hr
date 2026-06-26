package com.ecommint.accounthr.dto.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceSource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Servisler ekranının (E3-02) satır gösterimi — master liste yönetimi DTO'su.
 *
 * <p>E1-07'nin minimal {@link ServiceDto}'sunu genişletir: "Aktif Aylar" ve fatura
 * iletişim ({@link ServiceContactDto}) listesi de döner. Entity SIZDIRMAZ: lazy
 * ilişkiler (provider/defaultCard/usingTeam/contacts) düz alanlara indirgenir; JSON
 * serileştirmede lazy-proxy/döngü riski yoktur. Para {@link BigDecimal}, tarihler
 * ISO-8601.
 */
@Schema(description = "Servis (abonelik) master liste satırı — Servisler ekranı gösterimi")
public record ServiceResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "Claude AI") String name,
        @Schema(description = "Fatura kesen firma adı", example = "Anthropic", nullable = true)
        String providerName,
        @Schema(description = "Varsayılan kartın son 4 hanesi", example = "3800", nullable = true)
        String cardLast4,
        @Schema(description = "Servisi kullanan takım adı", example = "Engineering", nullable = true)
        String usingTeamName,
        @Schema(example = "MONTHLY") Frequency frequency,
        @Schema(example = "YES") ActiveState activeState,
        @Schema(description = "Servisin görüldüğü/beklendiği aylar (virgüllü, ör. \"2026-01, 2026-02\")",
                example = "2026-01, 2026-02, 2026-03", nullable = true)
        String activeMonths,
        @Schema(description = "Yaklaşık aylık TL tutarı", example = "1200.00", nullable = true)
        BigDecimal approxAmountTry,
        @Schema(description = "Operasyonel toplama dahil olmayan bilgi-amaçlı kalem mi", example = "false")
        boolean informational,
        @Schema(example = "EMAIL", nullable = true) InvoiceSource invoiceSource,
        @Schema(description = "Kullanım amacı", nullable = true) String purpose,
        @Schema(description = "Özel notlar", nullable = true) String notes,
        @Schema(description = "Fatura iletişim kayıtları (e-posta + kaynak + primary)")
        List<ServiceContactDto> contacts,
        @Schema(description = "Oluşturulma zamanı (ISO-8601)") LocalDateTime createdAt,
        @Schema(description = "Son güncelleme zamanı (ISO-8601)") LocalDateTime updatedAt) {
}
