package com.ecommint.accounthr.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * E3-01 — Aylık dashboard özeti (KPI + fatura durumu dağılımı).
 *
 * <p>Tüm sayılar/toplamlar DB tarafında (SQL/JPQL) hesaplanır; ham satırlar
 * uygulamaya çekilmez. {@code statusCounts} her zaman 5 durumun TAMAMINI içerir
 * (o ay 0 olsa bile) — frontend donut grafiğinin lejantı stabil kalsın diye.
 * Sıra: FOUND, E_INVOICE, EXPECTED, TO_INVESTIGATE, IGNORED.
 *
 * <p>Bilinmeyen/boş ay için endpoint 500 ATMAZ: tüm değerler sıfır, {@code statusCounts}
 * yine 5 girdiyle (hepsi 0) döner, {@code month} istek değeri olduğu gibi yansıtılır.
 */
@Schema(description = "Aylık dashboard özeti: KPI'lar + fatura durumu dağılımı.")
public record DashboardSummary(

        @Schema(description = "İstenen ay (echo). Format YYYY-MM.", example = "2026-03")
        String month,

        @Schema(description = "Ana harcamaların TL toplamı (informational=false). Veri yoksa 0.",
                example = "125430.50")
        BigDecimal totalTry,

        @Schema(description = "Fatura durumu dağılımı — her zaman 5 durumun tamamı "
                + "(FOUND, E_INVOICE, EXPECTED, TO_INVESTIGATE, IGNORED), o ay 0 olsa bile.")
        List<StatusCount> statusCounts,

        @Schema(description = "Eksik (beklenen) fatura sayısı = EXPECTED.", example = "4")
        long missingCount,

        @Schema(description = "Bulunan fatura sayısı = FOUND + E_INVOICE.", example = "12")
        long foundCount,

        @Schema(description = "Araştırılacak fatura sayısı = TO_INVESTIGATE.", example = "1")
        long investigateCount,

        @Schema(description = "Ana harcama (satır) sayısı (informational=false).", example = "17")
        long expenseCount) {

    /**
     * Tek bir fatura durumunun bu aydaki adedi ve UI rengi.
     * {@code colorHex} tek kaynak olan {@code StatusColors.STATUS_TO_HEX}'ten gelir;
     * frontend status badge / donut renklerini buradan aynalar.
     */
    @Schema(description = "Bir fatura durumunun adedi + UI hex rengi (tek kaynak: StatusColors).")
    public record StatusCount(

            @Schema(description = "Fatura durumu.", example = "FOUND")
            InvoiceStatus status,

            @Schema(description = "Bu aydaki adet.", example = "8")
            long count,

            @Schema(description = "UI dolgu rengi (6 hane RGB, StatusColors).", example = "4CAF50")
            String colorHex) {
    }
}
