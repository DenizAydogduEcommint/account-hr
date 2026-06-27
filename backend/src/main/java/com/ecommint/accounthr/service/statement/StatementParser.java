package com.ecommint.accounthr.service.statement;

/**
 * Banka ekstresi / dönem-içi hareket dökümü parser SPI'si (E4-01).
 *
 * <p>Tek giriş noktası: dosya içeriği (byte) + dosya adı (format tespiti için) + kartın son
 * 4 hanesi (hangi bankanın/karttın ekstresi olduğunu seçmek için). Uygulama formatı
 * tespit eder (.xlsx/.xls → Excel, .docx → Word, .doc/.pdf → desteklenmiyor) ve banka-özgül
 * satır çıkarımını {@link BankStatementExtractor}'a delege eder.
 *
 * <p><b>SCOPE (E4-01):</b> banka-özgül satır çıkarımı HENÜZ tanımlı değildir (gerçek örnek
 * ekstre beklenmektedir). Mevcut implementasyon formatı tespit eder, dokümanı POI ile açar
 * (plumbing'in çalıştığını kanıtlar) ve BOŞ işlem listesi + net bir uyarı döner. Gerçek bir
 * extractor sonradan {@link BankStatementExtractor} arkasına eklenebilir.
 */
public interface StatementParser {

    /**
     * Bir ekstre dosyasını parse eder.
     *
     * @param content   dosya içeriği (byte[])
     * @param filename  orijinal dosya adı (uzantı → format tespiti)
     * @param cardLast4 kartın son 4 hanesi (banka/extractor seçimi için)
     * @return parse edilmiş işlemler + uyarılar (asla null; placeholder boş liste + uyarı döner)
     */
    ParseResult parse(byte[] content, String filename, String cardLast4);
}
