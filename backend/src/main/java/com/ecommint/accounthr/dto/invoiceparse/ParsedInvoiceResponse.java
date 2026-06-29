package com.ecommint.accounthr.dto.invoiceparse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ecommint.accounthr.service.invoiceparse.ParsedInvoice;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code POST /api/v1/invoices/parse} yanıtı (E5-03 BACKEND).
 *
 * <p>Bir fatura PDF'inden çıkarılan alanlar; istemci bunlarla fatura formunu otomatik
 * doldurur. Tüm alanlar nullable — bir desen eşleşmediyse alan {@code null} olur ve
 * {@link #warnings()} doldurulur. Yanıt {@code rawText} İÇERMEZ; ham metin yalnızca
 * sunucu tarafı hata ayıklama içindir ve gizli müşteri verisi sızdırmamak için
 * yanıttan kasıtlı olarak dışlanır.
 *
 * @param invoiceNumber fatura numarası (nullable)
 * @param issueDate     düzenlenme tarihi (nullable)
 * @param totalAmount   toplam tutar, scale 2 (nullable)
 * @param currency      para birimi kodu USD/EUR/TRY/GBP (nullable)
 * @param vatAmount     KDV tutarı (nullable — KDV'siz faturada null)
 * @param vatRate       KDV oranı yüzde (nullable, ör. 20)
 * @param providerName  faturayı kesen firma (nullable)
 * @param warnings      eşleşmeyen alanlar için uyarılar (asla null; boş olabilir)
 */
@Schema(description = "Fatura PDF'inden çıkarılan alanlar (otomatik doldurma için)")
public record ParsedInvoiceResponse(
        @Schema(example = "E15F9A97-0013") String invoiceNumber,
        @Schema(example = "2025-10-07") LocalDate issueDate,
        @Schema(example = "36.00") BigDecimal totalAmount,
        @Schema(example = "USD") String currency,
        @Schema(example = "6.00") BigDecimal vatAmount,
        @Schema(example = "20") BigDecimal vatRate,
        @Schema(example = "OpenAI, LLC") String providerName,
        List<String> warnings) {

    /** İç modelden (rawText hariç) yanıt DTO'su üretir. */
    public static ParsedInvoiceResponse from(ParsedInvoice p) {
        return new ParsedInvoiceResponse(
                p.invoiceNumber(),
                p.issueDate(),
                p.totalAmount(),
                p.currency(),
                p.vatAmount(),
                p.vatRate(),
                p.providerName(),
                p.warnings());
    }
}
