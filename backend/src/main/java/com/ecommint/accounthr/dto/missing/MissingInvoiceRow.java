package com.ecommint.accounthr.dto.missing;

import java.math.BigDecimal;

import com.ecommint.accounthr.domain.enums.Frequency;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * E3-04 — Bir ayda faturası eksik olan tek bir servisin satırı.
 *
 * <p>"Eksik" = kontrol kümesindeki servis (Aktif=Evet, bilgi-amaçlı değil; Aylık her ay
 * ya da Yıllık ise yalnızca Aktif Aylar listesindeki ay) için o dönemde durumu
 * {@code FOUND}/{@code E_INVOICE} olan hiçbir harcaması YOK demektir. Bekleniyor
 * ({@code EXPECTED}) ya da hiç satır olmaması da eksik sayılır.
 */
@Schema(description = "Bir ayda faturası eksik olan servis satırı (servis ↔ ay çapraz doğrulama).")
public record MissingInvoiceRow(

        @Schema(description = "Servis (master) kimliği.", example = "42")
        Long serviceId,

        @Schema(description = "Servis/abonelik adı.", example = "Zoom Workplace Pro")
        String serviceName,

        @Schema(description = "Fatura kesen firma (sağlayıcı) adı.", example = "Zoom Video Communications")
        String providerName,

        @Schema(description = "Servisin varsayılan kartının son 4 hanesi; yoksa null.",
                example = "3800", nullable = true)
        String cardLast4,

        @Schema(description = "Servis frekansı (MONTHLY/YEARLY). Kontrol kümesinde yalnızca bu ikisi olur.",
                example = "MONTHLY")
        Frequency frequency,

        @Schema(description = "Servisin yaklaşık aylık TL tutarı (master listeden); yoksa null.",
                example = "1250.00", nullable = true)
        BigDecimal approxAmountTry,

        @Schema(description = "Faturadan sorumlu birincil iletişim e-postası; yoksa null.",
                example = "accounting@e-commint.com", nullable = true)
        String contactEmail,

        @Schema(description = "Servisin 'Aktif Aylar' verbatim alanı (virgüllü YYYY-MM); olmayabilir.",
                example = "2026-01, 2026-02, 2026-03", nullable = true)
        String activeMonths,

        @Schema(description = "Servisin herhangi bir harcamada en son görüldüğü ay kodu; hiç yoksa null.",
                example = "2026-02", nullable = true)
        String lastSeenMonth) {
}
