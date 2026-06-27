package com.ecommint.accounthr.dto.incoming;

import java.time.Instant;

import com.ecommint.accounthr.domain.IncomingInvoice;

/**
 * Bir ham/gelen fatura (incoming invoice) satırının dış (HTTP) gösterimi (E5-02).
 *
 * @param id          satır id
 * @param source      kaynak (DRIVE_WAITING / MAIL)
 * @param sourceRef   kaynak referansı / idempotency anahtarı (drive göreli yolu)
 * @param fileName    orijinal dosya adı
 * @param storedPath  STORAGE_ROOT'a göreli kopya yolu
 * @param sha256      içerik SHA-256 (64 hex)
 * @param receivedAt  toplanma (pull) anı
 * @param status      NEW / MATCHED / IGNORED
 * @param notes       serbest not (nullable)
 */
public record IncomingInvoiceResponse(
        Long id,
        String source,
        String sourceRef,
        String fileName,
        String storedPath,
        String sha256,
        Instant receivedAt,
        String status,
        String notes) {

    public static IncomingInvoiceResponse from(IncomingInvoice e) {
        return new IncomingInvoiceResponse(
                e.getId(),
                e.getSource() == null ? null : e.getSource().name(),
                e.getSourceRef(),
                e.getFileName(),
                e.getStoredPath(),
                e.getSha256(),
                e.getReceivedAt(),
                e.getStatus() == null ? null : e.getStatus().name(),
                e.getNotes());
    }
}
