package com.ecommint.accounthr.dto.incoming;

import java.util.List;

/**
 * E5-02 — Drive {@code waiting/} pull + ingest işleminin özet sonucu.
 *
 * @param pulledCount  landing dizininde rclone sonrası BULUNAN toplam dosya sayısı (bu çağrıda
 *                     taranan dosyalar; rclone copy değişmeyenleri yeniden indirmez)
 * @param newCount     bu çağrıda YENİ kaydedilen ham fatura sayısı
 * @param skippedCount idempotency (aynı sourceRef veya aynı sha256) nedeniyle atlanan dosya sayısı
 * @param newInvoices  bu çağrıda oluşturulan ham fatura satırları
 * @param message      insan-okur durum mesajı
 */
public record IncomingPullResult(
        int pulledCount,
        int newCount,
        int skippedCount,
        List<IncomingInvoiceResponse> newInvoices,
        String message) {
}
