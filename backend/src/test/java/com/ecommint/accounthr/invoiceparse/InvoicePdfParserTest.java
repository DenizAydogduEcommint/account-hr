package com.ecommint.accounthr.invoiceparse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ecommint.accounthr.service.invoiceparse.InvoicePdfParser;
import com.ecommint.accounthr.service.invoiceparse.ParsedInvoice;

/**
 * E5-03 — {@link InvoicePdfParser} birim testleri. Tüm girdi PDF'leri
 * {@link SyntheticInvoicePdf} ile in-memory üretilir; gerçek/gizli fatura kullanılmaz.
 *
 * <p>Senaryolar gerçek örneklerin metin düzeninden türetilmiştir: KDV'li İngilizce
 * (OpenAI), KDV'siz, Türkçe (virgül ondalık), Wondershare (ISO tarih, $'sız sayı) ve
 * bozuk/PDF-olmayan girdi.
 */
class InvoicePdfParserTest {

    private final InvoicePdfParser parser = new InvoicePdfParser();

    @Test
    void parsesEnglishStripeInvoiceWithVat() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Invoice",
                "Invoice number TEST-001",
                "Date of issue October 7, 2025",
                "Date due October 7, 2025",
                "OpenAI, LLC",
                "1455 3rd Street",
                "$36.00 USD due October 7, 2025",
                "Description Qty Unit price Tax Amount",
                "OpenAI API usage credit 1 $30.00 20% $30.00",
                "Subtotal $30.00",
                "Total excluding tax $30.00",
                "VAT - Turkey (20% on $30.00) $6.00",
                "Total $36.00",
                "Amount due $36.00 USD"));

        ParsedInvoice p = parser.parse(pdf);

        assertThat(p.invoiceNumber()).isEqualTo("TEST-001");
        assertThat(p.issueDate()).isEqualTo(LocalDate.of(2025, 10, 7));
        assertThat(p.totalAmount()).isEqualByComparingTo(new BigDecimal("36.00"));
        assertThat(p.currency()).isEqualTo("USD");
        assertThat(p.vatAmount()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(p.vatRate()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(p.providerName()).isEqualTo("OpenAI, LLC");
        assertThat(p.rawText()).contains("Invoice number TEST-001");
    }

    @Test
    void parsesInvoiceWithoutVat() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Invoice",
                "Invoice number ZDHBNJH0-0001",
                "Date of issue December 7, 2025",
                "Kapwing, Inc.",
                "$24.00 USD due December 7, 2025",
                "Kapwing Pro 1 $24.00 $24.00",
                "Subtotal $24.00",
                "Total $24.00",
                "Amount due $24.00 USD"));

        ParsedInvoice p = parser.parse(pdf);

        assertThat(p.invoiceNumber()).isEqualTo("ZDHBNJH0-0001");
        assertThat(p.issueDate()).isEqualTo(LocalDate.of(2025, 12, 7));
        assertThat(p.totalAmount()).isEqualByComparingTo(new BigDecimal("24.00"));
        assertThat(p.currency()).isEqualTo("USD");
        assertThat(p.providerName()).isEqualTo("Kapwing, Inc.");
        // KDV yok → null, ama yine de uyarı eklenir.
        assertThat(p.vatAmount()).isNull();
        assertThat(p.vatRate()).isNull();
        assertThat(p.warnings()).anyMatch(w -> w.contains("KDV"));
    }

    @Test
    void parsesTurkishInvoiceWithCommaDecimals() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Fatura",
                "Fatura numarasi D610AAA1-0019",
                "Islem tarihi 15 Ekim 2025",
                "Vade tarihi 15 Ekim 2025",
                "OpenAI, LLC",
                "15 Ekim 2025 tarihine kadar $120,00 odeme gerekiyor",
                "ChatGPT Business Subscription 4 $30,00 $120,00",
                "Ara toplam $120,00",
                "Toplam $120,00",
                "Vadesi gelen tutar $120,00"));

        ParsedInvoice p = parser.parse(pdf);

        assertThat(p.invoiceNumber()).isEqualTo("D610AAA1-0019");
        assertThat(p.issueDate()).isEqualTo(LocalDate.of(2025, 10, 15));
        assertThat(p.totalAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(p.currency()).isEqualTo("USD");
        assertThat(p.providerName()).isEqualTo("OpenAI, LLC");
    }

    @Test
    void parsesWondershareStyleIsoDateNoCurrencySymbol() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "INVOICE",
                "Wondershare Global Limited",
                "Order Date: 2025-12-07",
                "Invoice Number: INV26004000019S",
                "Currency: USD",
                "Media.io 200 Credits 16.99 1 16.31",
                "Net Total (USD): 13.59",
                "VAT 20.00%: 2.72",
                "Grand Total (USD): 16.31"));

        ParsedInvoice p = parser.parse(pdf);

        assertThat(p.invoiceNumber()).isEqualTo("INV26004000019S");
        assertThat(p.issueDate()).isEqualTo(LocalDate.of(2025, 12, 7));
        assertThat(p.totalAmount()).isEqualByComparingTo(new BigDecimal("16.31"));
        assertThat(p.currency()).isEqualTo("USD");
        assertThat(p.vatRate()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(p.vatAmount()).isEqualByComparingTo(new BigDecimal("2.72"));
        assertThat(p.providerName()).isEqualTo("Wondershare Global Limited");
    }

    @Test
    void parsesLucidchartAbbreviatedMonth() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Lucid Software Inc.",
                "Invoice",
                "Invoice # 19501968",
                "Billed On Nov 7, 2025",
                "Lucidchart Individual Monthly 1 $11.00 $11.00",
                "Subtotal $11.00",
                "Total $11.00",
                "Amount Due $0.00"));

        ParsedInvoice p = parser.parse(pdf);

        assertThat(p.invoiceNumber()).isEqualTo("19501968");
        assertThat(p.issueDate()).isEqualTo(LocalDate.of(2025, 11, 7));
        assertThat(p.totalAmount()).isEqualByComparingTo(new BigDecimal("11.00"));
        assertThat(p.currency()).isEqualTo("USD");
        assertThat(p.providerName()).isEqualTo("Lucid Software Inc.");
    }

    /**
     * Fix 1 (kritik) — ABD binlik gruplaması "1,234" (ondalık yok) artık 1234 olarak
     * okunur; eski kod yanlışlıkla 1.234 (~1.23) üretiyordu. parseNumberFlexible paket-özel
     * ve farklı pakette olduğu için sentetik faturanın toplamı üzerinden doğrulanır.
     */
    @Test
    void parsesUsThousandsTotalWithoutCents() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Invoice",
                "Invoice number TEST-THOUSANDS",
                "Date of issue October 7, 2025",
                "Acme, Inc.",
                "Bulk service 1 $1,234 $1,234",
                "Subtotal $1,234",
                "Total $1,234",
                "Amount due $1,234 USD"));

        ParsedInvoice p = parser.parse(pdf);

        // Hata öncesi: 1.23 ; düzeltme sonrası: 1234.00
        assertThat(p.totalAmount()).isEqualByComparingTo(new BigDecimal("1234"));
    }

    /**
     * Fix 1 — çeşitli ABD/TR sayı biçimleri toplam üzerinden doğrulanır:
     * 1,234,567 -> 1234567 ; 1.234,56 -> 1234.56 ; 1,234.56 -> 1234.56 ; 120,00 -> 120.00.
     */
    @Test
    void parsesMillionsUsThousands() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Invoice", "Invoice number N1", "Date of issue October 7, 2025",
                "Total $1,234,567", "Amount due $1,234,567 USD"));
        assertThat(parser.parse(pdf).totalAmount()).isEqualByComparingTo(new BigDecimal("1234567"));
    }

    @Test
    void parsesTrThousandsAndDecimal() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Fatura", "Fatura numarasi N2", "Islem tarihi 7 Ekim 2025",
                "Toplam $1.234,56", "Vadesi gelen tutar $1.234,56"));
        assertThat(parser.parse(pdf).totalAmount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void parsesUsThousandsWithDecimal() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Invoice", "Invoice number N3", "Date of issue October 7, 2025",
                "Total $1,234.56", "Amount due $1,234.56 USD"));
        assertThat(parser.parse(pdf).totalAmount()).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    void parsesPlainDecimalNoSeparators() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "INVOICE", "Wondershare Global Limited", "Order Date: 2025-12-07",
                "Invoice Number: N4", "Currency: USD",
                "Grand Total (USD): 16.31"));
        assertThat(parser.parse(pdf).totalAmount()).isEqualByComparingTo(new BigDecimal("16.31"));
    }

    /**
     * Fix 2 (önemli) — satır-içi açıklamada "Total" geçen bir ürün satırı (büyük indirim
     * öncesi birim fiyatla) toplam OLARAK seçilmemeli; gerçek "Amount due" döndürülmeli.
     */
    @Test
    void lineItemContainingWordTotalDoesNotWinOverAmountDue() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Invoice",
                "Invoice number TEST-DESC",
                "Date of issue October 7, 2025",
                "Acme, Inc.",
                // Satır-İÇİ "Total" + büyük tutar: eski çapasız regex bunu $999.00 olarak
                // yakalardı; çapalı (^\s*) regex ile artık eşleşmemeli.
                "Plan Annual Total Value 1 Total: $999.00 then $50.00",
                "Subtotal $50.00",
                "Total $50.00",
                "Amount due $50.00 USD"));

        ParsedInvoice p = parser.parse(pdf);

        // Açıklamadaki $999.00 değil; kesin ödenecek tutar 50.00 dönmeli.
        assertThat(p.totalAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void garbageBytesProduceWarningsAndNeverThrow() {
        byte[] garbage = "this is not a pdf at all".getBytes();

        ParsedInvoice p = parser.parse(garbage);

        assertThat(p.invoiceNumber()).isNull();
        assertThat(p.issueDate()).isNull();
        assertThat(p.totalAmount()).isNull();
        assertThat(p.warnings()).isNotEmpty();
    }

    @Test
    void emptyBytesProduceWarningAndNeverThrow() {
        ParsedInvoice p = parser.parse(new byte[0]);

        assertThat(p.warnings()).anyMatch(w -> w.toLowerCase().contains("boş"));
        assertThat(p.invoiceNumber()).isNull();
    }
}
