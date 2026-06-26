package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.FileAsset;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {

    List<FileAsset> findByInvoiceId(Long invoiceId);

    /**
     * E3 deep-review #5 (N+1 fix) — Birden çok invoice'un dosyalarını TEK sorguda getirir
     * ({@code ExpenseQueryService.listFiles} per-invoice {@code findByInvoiceId} döngüsü
     * yerine). Çağıran tarafça invoice id'ye göre gruplanır + file id'ye göre tekilleştirilir.
     */
    List<FileAsset> findByInvoiceIdIn(java.util.Collection<Long> invoiceIds);

    /** Duplicate tespiti (bütünlük): aynı içerik hash'ine sahip kayıtlar. */
    List<FileAsset> findBySha256(String sha256);

    /** Idempotency (E2-03): aynı storage-root göreli yoluna sahip kayıt var mı? */
    List<FileAsset> findByFilePath(String filePath);

    boolean existsBySha256(String sha256);

    /**
     * True-duplicate kontrolü (E2-DR-1): aynı (fatura, içerik) ikilisi zaten var mı?
     * Bileşik tekil ({@code uq_files_invoice_sha256}) ile aynı tanecik — fatura-içi
     * duplicate'i (aynı faturaya aynı sha) tespit eder. NOT: {@code invoiceId == null}
     * çağrısı {@code invoice_id = null} üretir ve hiçbir satırla eşleşmez (her zaman false);
     * null-invoice content-dedup uygulama düzeyinde {@link #findBySha256} ile ele alınır.
     */
    boolean existsByInvoiceIdAndSha256(Long invoiceId, String sha256);

    /**
     * Duplicate tespiti (mantıksal): aynı (provider, invoice_no) ikilisine bağlı
     * mevcut dosyalar. invoice → provider ve invoice_no üzerinden gider.
     */
    List<FileAsset> findByInvoiceProviderIdAndInvoiceInvoiceNo(Long providerId, String invoiceNo);
}
