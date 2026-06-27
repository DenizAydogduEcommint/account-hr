package com.ecommint.accounthr.service.statement;

import java.util.List;

/**
 * Banka-özgül satır çıkarımı SPI'si (E4-01). {@link DefaultStatementParser} formatı tespit
 * edip dokümanı POI ile açtıktan sonra, AÇILMIŞ doküman modelinden gerçek işlem satırlarını
 * çıkarmayı bu arayüze delege eder.
 *
 * <p>Böylece her banka/kart için ayrı bir extractor "drop-in" olarak eklenebilir; parser'ın
 * format-tespiti/açma plumbing'i değişmez. Mevcut tek implementasyon
 * {@link PlaceholderBankStatementExtractor} olup BOŞ liste + uyarı döner (gerçek örnek
 * ekstre gelene kadar).
 *
 * @param <D> POI doküman tipi (XSSFWorkbook / HSSFWorkbook / XWPFDocument)
 */
public interface BankStatementExtractor<D> {

    /**
     * Bu extractor verilen kart (banka) + format için satır çıkarımını yapabiliyor mu?
     * Gerçek extractor'lar {@code cardLast4} + format'a bakarak kendi bankalarını seçer.
     */
    boolean supports(String cardLast4, StatementFormat format);

    /**
     * Açılmış POI dokümanından işlem satırlarını çıkarır.
     *
     * @param document  açılmış POI doküman modeli (parser tarafından açılıp kapatılır)
     * @param cardLast4 kartın son 4 hanesi
     * @param format    tespit edilen format
     * @return çıkarılan işlemler (placeholder: boş liste)
     */
    List<ParsedTxn> extract(D document, String cardLast4, StatementFormat format);
}
