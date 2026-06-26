package com.ecommint.accounthr.dto.expense;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.ExpenseSource;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * E3-03 — Aylık harcamalar ekranındaki tek bir satır (Excel ay sheet'inin 12 kolonu).
 *
 * <p>Para değerleri ham {@link BigDecimal} olarak döner; format ({@code #.##0,00},
 * {@code #.##0,00 ₺}) frontend'de uygulanır. {@code transactionDate} ISO {@code YYYY-MM-DD}
 * olarak serialize edilir; {@code DD.MM.YYYY} gösterimi frontend'in işidir.
 *
 * <p>Bir expense'in birden çok invoice'u olabilir (iade / invoice+receipt / duplicate);
 * satırda gösterilen {@code invoiceStatus}/{@code invoiceNote}/{@code invoiceColorHex}
 * temsilci (en güncel = en yüksek id'li) invoice'tan gelir. Hiç invoice yoksa null'dur.
 */
@Schema(description = "Aylık harcamalar tablosunun tek satırı (12 kolon).")
public record ExpenseRow(

        @Schema(description = "Harcama (expense) id'si — detay/aksiyon için.", example = "42")
        Long id,

        @Schema(description = "Kart ekstresindeki işlem tarihi (ISO YYYY-MM-DD). Bekleniyor satırında null.",
                example = "2026-03-15", nullable = true)
        LocalDate transactionDate,

        @Schema(description = "Hizmet (servis/abonelik) adı.", example = "Claude AI")
        String serviceName,

        @Schema(description = "Fatura kesen firma (sağlayıcı) adı.", example = "Anthropic", nullable = true)
        String providerName,

        @Schema(description = "Orijinal (döviz) tutar — ham sayı. TL ödemede null olabilir.",
                example = "20.00", nullable = true)
        BigDecimal amount,

        @Schema(description = "Para birimi.", example = "USD")
        Currency currency,

        @Schema(description = "Kart ekstresindeki TL karşılığı — ham sayı.", example = "680.50", nullable = true)
        BigDecimal amountTry,

        @Schema(description = "Kartın son 4 hanesi.", example = "3800", nullable = true)
        String cardLast4,

        @Schema(description = "Kullanan takım adı.", example = "Backend", nullable = true)
        String usingTeam,

        @Schema(description = "Amaç / kısa açıklama.", example = "LLM API", nullable = true)
        String purpose,

        @Schema(description = "Muhasebe e-postası (servisin birincil iletişim e-postası).",
                example = "accounting@e-commint.com", nullable = true)
        String accountingEmail,

        @Schema(description = "Fatura durumu (temsilci invoice). Hiç invoice yoksa null.",
                example = "FOUND", nullable = true)
        InvoiceStatus invoiceStatus,

        @Schema(description = "Fatura durumu UI hex rengi (tek kaynak StatusColors). Status null ise null.",
                example = "4CAF50", nullable = true)
        String invoiceColorHex,

        @Schema(description = "Fatura notu (dosya path'i ve/veya açıklama). Yoksa null.",
                example = "faturalar/2026-03/claude_mart.pdf", nullable = true)
        String invoiceNote,

        @Schema(description = "Satırın kaynağı: STATEMENT (ekstre/importer) veya MANUAL "
                + "(elle girilen satır, E3-06). Eski satırlarda STATEMENT.",
                example = "MANUAL")
        ExpenseSource source) {
}
