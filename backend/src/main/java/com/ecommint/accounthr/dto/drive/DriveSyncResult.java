package com.ecommint.accounthr.dto.drive;

import java.util.List;

/**
 * E2-06 — Drive {@code waiting/} pull işleminin özet sonucu.
 *
 * @param pulledCount lokal waiting dizinine bu çağrıda YENİ inen dosya sayısı
 * @param newFiles    yeni inen dosyaların adları (dizin değil; sadece dosya adı)
 * @param skipped     köprü kapalı olduğu için işlem atlandı mı ({@code enabled=false})
 * @param message     insan-okur durum mesajı (no-op / başarı / sayım)
 */
public record DriveSyncResult(
        int pulledCount,
        List<String> newFiles,
        boolean skipped,
        String message) {

    /** Köprü kapalıyken dönülen no-op sonucu. */
    public static DriveSyncResult skipped(String message) {
        return new DriveSyncResult(0, List.of(), true, message);
    }
}
