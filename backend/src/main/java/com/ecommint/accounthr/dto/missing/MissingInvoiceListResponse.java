package com.ecommint.accounthr.dto.missing;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * E3-10 — {@code GET /api/v1/missing-invoices} yanıt zarfı.
 *
 * <p>Eksik servis satırlarına ek olarak KPI iki alanı taşır: {@code count} (eksik servis
 * sayısı = {@code items.size()}) ve {@code approxTotalTry} (eksik servislerin "yaklaşık
 * TL tutarı" alanlarının toplamı = belgelenememiş tahmini harcama). {@code approxTotalTry}
 * "yaklaşık"tır (her satırın en son görülen ayının TL'sinden gelir); yaklaşık değeri olmayan
 * satır {@code count}'a girer ama toplama 0 ekler.
 *
 * <p>Bu zarf, eski "çıplak liste" yanıtının yerini alır (BREAKING): frontend artık
 * {@code response.items} üzerinden satırlara erişir.
 */
@Schema(description = "Eksik fatura listesi + KPI (sayı ve yaklaşık TL toplamı).")
public record MissingInvoiceListResponse(

        @Schema(description = "Eksik servis satırları (servis ↔ ay çapraz doğrulama).")
        List<MissingInvoiceRow> items,

        @Schema(description = "Eksik servis sayısı (= items.size()).", example = "4")
        int count,

        @Schema(description = "Eksik servislerin yaklaşık TL toplamı (belgelenememiş tahmini "
                + "harcama). Yaklaşık tutarı olmayan satır 0 ekler. Ölçek 2.",
                example = "5320.00")
        BigDecimal approxTotalTry) {
}
