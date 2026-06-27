package com.ecommint.accounthr.dto.statement;

import java.util.List;

/**
 * {@code POST /api/v1/statements} (ve {@code GET /api/v1/statements/{batchRef}}) önizleme
 * yanıtı (E4-01).
 *
 * @param batchRef       bu yüklemeyi gruplayan referans (= dosyanın SHA-256'sı)
 * @param card           kartın son 4 hanesi
 * @param month          dönem kodu (YYYY-MM)
 * @param transactions   parse edilen ham işlemler (placeholder: boş)
 * @param warnings       parser uyarıları (placeholder: "satır çıkarma henüz tanımlı değil")
 * @param alreadyUploaded aynı (sha256+kart+dönem) için CONFIRMED batch zaten varsa true
 *                        (idempotency — mükerrer satır oluşturulmadı)
 */
public record StatementPreviewResponse(
        String batchRef,
        String card,
        String month,
        List<StatementTxnDto> transactions,
        List<String> warnings,
        boolean alreadyUploaded) {
}
