package com.ecommint.accounthr.service.invoiceparse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bir fatura PDF'inden çıkarılan yapılandırılmış alanlar (E5-03 BACKEND).
 *
 * <p>TÜM alanlar nullable'dır: ilgili desen eşleşmezse alan {@code null} kalır ve
 * {@link #warnings()} listesine bir uyarı eklenir. Parser asla istisna fırlatmaz —
 * çözümlenemeyen/bozuk bir PDF için bile eldeki kadarını + uyarıları döndürür.
 *
 * @param invoiceNumber fatura numarası (ör. "E15F9A97-0013", "19501968", "INV26004000019S")
 * @param issueDate     düzenlenme/işlem tarihi (LocalDate); "Month D, YYYY", "MMM D, YYYY",
 *                      "YYYY-MM-DD" ve Türkçe "D Ay YYYY" biçimlerini çözer
 * @param totalAmount   toplam tutar (BigDecimal, scale 2); $ / ₺ / virgül temizlenir
 * @param currency      para birimi kodu ("USD" / "EUR" / "TRY"); bulunamazsa null
 * @param vatAmount     KDV tutarı (BigDecimal, scale 2); KDV satırı yoksa null
 * @param vatRate       KDV oranı yüzde olarak (ör. 20); yoksa null
 * @param providerName  faturayı kesen firma (ör. "OpenAI, LLC", "Kapwing, Inc.")
 * @param rawText       PDFBox ile çıkarılan ham metin (hata ayıklama için)
 * @param warnings      eşleşmeyen/şüpheli alanlar için insan-okur uyarılar
 */
public record ParsedInvoice(
        String invoiceNumber,
        LocalDate issueDate,
        BigDecimal totalAmount,
        String currency,
        BigDecimal vatAmount,
        BigDecimal vatRate,
        String providerName,
        String rawText,
        List<String> warnings) {
}
