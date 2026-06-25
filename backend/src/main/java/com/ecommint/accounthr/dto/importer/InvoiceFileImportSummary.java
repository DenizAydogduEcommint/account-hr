package com.ecommint.accounthr.dto.importer;

import java.util.List;

/**
 * E2-03 — {@code faturalar/} klasör tarama + kopyalama + invoice/files eşleme
 * işleminin özet raporu.
 *
 * <p>Sayım alanları:
 * <ul>
 *   <li>{@code scanned}: taranan fizik dosya sayısı (dizinler, dotfile'lar, .DS_Store hariç).</li>
 *   <li>{@code copied}: storage kökü altına fiilen YENİ kopyalanan dosya sayısı
 *       (idempotent re-run'da 0).</li>
 *   <li>{@code matched}: bir invoice'a bağlanan dosya sayısı (note eşleşmesi + türev kardeşler).</li>
 *   <li>{@code unmatched}: hiçbir invoice'a bağlanamayan dosya sayısı (trash dahil).</li>
 *   <li>{@code trashed}: {@code trash/} altında olduğu için bilerek bağlanmayan dosya sayısı.</li>
 *   <li>{@code duplicates}: aynı içerik (SHA-256) tekrar görülen veya adı "duplicate" içeren dosyalar.</li>
 *   <li>{@code newFileRows}: bu run'da {@code files} tablosuna yazılan YENİ satır sayısı
 *       (idempotency kanıtı; 2. run'da 0).</li>
 * </ul>
 *
 * <p>{@code unmatchedFiles} eşleşmeyen dosyaların göreli yollarını listeler (rapor/inceleme için).
 */
public record InvoiceFileImportSummary(
        int scanned,
        int copied,
        int matched,
        int unmatched,
        int trashed,
        int duplicates,
        int newFileRows,
        List<String> unmatchedFiles) {
}
