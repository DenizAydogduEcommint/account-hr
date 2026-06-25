package com.ecommint.accounthr.dto.importer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E2-05 — Migrasyon doğrulama / mutabakat raporu (salt-okunur).
 *
 * <p>E2-01..E2-04 migrasyonunun doğru ve eksiksiz olduğunu kanıtlayan denetlenebilir
 * rapor. Hiçbir mutasyon yapmaz; yalnızca Excel ile DB'yi karşılaştırıp sonucu döner.
 *
 * <p>İçerik:
 * <ul>
 *   <li>{@code periods}: her ay-period için Excel ana {@code TOPLAM:} TL'si ↔ DB ana
 *       harcama TL toplamı (IGNORED bilgi-amaçlı bölümler HARİÇ) ve satır sayısı
 *       karşılaştırması; durum {@link PeriodStatus}.</li>
 *   <li>{@code filesPhysical}: storage kökü altındaki fiziksel dosya sayısı. Storage kökü
 *       taranamadıysa (I/O hatası) {@code null} olur — bu durumda neden {@code
 *       inconsistencies}'te açıklanır (negatif/sentinel değer sızdırılmaz).</li>
 *   <li>{@code filesDbRows}: {@code files} tablosundaki kayıt sayısı. E2-03 SHA-256 ile
 *       içerik-duplicate'lerini dedup ettiğinden fiziksel ≥ DB olabilir; fark BAŞARISIZLIK
 *       değil, {@code inconsistencies}'te açıklanan bir nottur.</li>
 *   <li>{@code statusDistribution}: DB invoice durum→adet dağılımı (E2-04 ile tutarlı).</li>
 *   <li>{@code idempotencyKeys}: her importer'ın doğal/idempotency anahtarı (dokümantasyon;
 *       importer YENİDEN çalıştırılmaz — tekrar import 0 yeni satır üretir, E2-01..03'te
 *       zaten kanıtlandı).</li>
 *   <li>{@code inconsistencies}: insan-okunur tutarsızlık satırları (hangi period/tutar/
 *       satır tutmadı, hangi uyarı verildi).</li>
 *   <li>{@code ok}: hiç MISMATCH yoksa {@code true} (WARNING'lere izin verilir).</li>
 * </ul>
 */
public record ReconciliationReport(
        List<PeriodReconciliation> periods,
        Long filesPhysical,
        long filesDbRows,
        Map<InvoiceStatus, Long> statusDistribution,
        Map<String, String> idempotencyKeys,
        List<String> inconsistencies,
        boolean ok) {

    /** Period bazlı mutabakat durumu. */
    public enum PeriodStatus {
        /** Excel TOPLAM ↔ DB toplam ±0.01 içinde ve satır sayıları eşit. */
        MATCH,
        /** Tutar veya satır sayısı tutmuyor (sert tutarsızlık). */
        MISMATCH,
        /** Excel veya DB toplamı NULL/boş (ör. Nisan kısmi ay) — sert hata değil. */
        WARNING
    }

    /**
     * Tek period için Excel↔DB mutabakat satırı.
     *
     * @param periodCode    period kodu (ör. 2026-01)
     * @param sheetName     eşleşen Excel sheet adı (ör. Ocak); sheet yoksa null
     * @param excelTotal    Excel ana {@code TOPLAM:} TL değeri (yoksa null)
     * @param dbTotal       DB ana harcama TL toplamı, IGNORED hariç (yoksa null)
     * @param diff          {@code excelTotal - dbTotal} (biri null ise null)
     * @param excelRowCount Excel'deki ana harcama satırı sayısı (TOPLAM/bilgi/footer hariç)
     * @param dbRowCount    DB'deki ana expense sayısı (IGNORED hariç)
     * @param status        period mutabakat durumu
     */
    public record PeriodReconciliation(
            String periodCode,
            String sheetName,
            BigDecimal excelTotal,
            BigDecimal dbTotal,
            BigDecimal diff,
            int excelRowCount,
            long dbRowCount,
            PeriodStatus status) {
    }
}
