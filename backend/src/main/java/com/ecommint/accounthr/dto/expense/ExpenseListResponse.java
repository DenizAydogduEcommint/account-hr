package com.ecommint.accounthr.dto.expense;

import java.math.BigDecimal;
import java.util.List;

import com.ecommint.accounthr.dto.PagedResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * E3-03 — Aylık harcamalar ekranı listeleme yanıtı.
 *
 * <p>Excel ay sheet'inin web karşılığı. {@code main} ANA (operasyonel,
 * {@code informational=false}) harcama satırlarının SAYFALI listesidir; filtre/arama
 * yalnızca bu satırlara uygulanır. {@code operationalTotalTry} bu ANA satırların TL
 * toplamıdır (E2-05 {@code sumMainAmountTryByPeriod} ile aynı kural — filtreden
 * bağımsız, dönem geneli).
 *
 * <p>{@code informationalRows} bilgi-amaçlı ({@code informational=true}: Multinet /
 * sigorta / vergi) satırları AYRI bir liste olarak taşır; bunlar {@code main} içinde
 * GÖRÜNMEZ ve {@code operationalTotalTry}'a DAHİL DEĞİLDİR. Kendi alt toplamları
 * {@code informationalTotalTry}'dadır.
 */
@Schema(description = "Aylık harcamalar listesi: ana (sayfalı) + bilgi-amaçlı satırlar + toplamlar.")
public record ExpenseListResponse(

        @Schema(description = "İstenen ay (echo). Format YYYY-MM.", example = "2026-03")
        String month,

        @Schema(description = "Ana (operasyonel, informational=false) harcama satırları — sayfalı. "
                + "Filtreler (kart/durum/arama) yalnızca bu listeye uygulanır.")
        PagedResponse<ExpenseRow> main,

        @Schema(description = "Operasyonel TL toplamı (informational=false, dönem geneli — filtreden bağımsız). "
                + "Veri yoksa 0.", example = "125430.50")
        BigDecimal operationalTotalTry,

        @Schema(description = "Bilgi-amaçlı (informational=true: Multinet/sigorta/vergi) satırlar — AYRI liste, "
                + "operasyonel toplama dahil DEĞİL.")
        List<ExpenseRow> informationalRows,

        @Schema(description = "Bilgi-amaçlı satırların TL alt toplamı (operasyonel toplama dahil değil). Yoksa 0.",
                example = "8500.00")
        BigDecimal informationalTotalTry) {
}
