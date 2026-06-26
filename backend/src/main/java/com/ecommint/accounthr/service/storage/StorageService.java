package com.ecommint.accounthr.service.storage;

import java.io.InputStream;
import java.time.LocalDate;

import org.springframework.core.io.Resource;

import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.enums.FileType;

/**
 * Fatura dosyalarını LOKAL dosya sisteminde yöneten servis (E1-04).
 *
 * <p>Kurallar (CLAUDE.md):
 * <ul>
 *   <li>Klasör = fatura tarihinden türetilen {@code YYYY-MM} (ödeme tarihi DEĞİL).</li>
 *   <li>Dosya adı = slugify(hizmet) + "_" + Türkçe ay adı + tip eki; çakışmada {@code _1}, {@code _2}.</li>
 *   <li>Duplicate: aynı (provider, invoice_no) VEYA aynı SHA-256 → {@link DuplicateFileException}.</li>
 *   <li>Güvenlik: dosya adı sanitize edilir; çözümlenen yol storage kökü ALTINDA kalmalı.</li>
 * </ul>
 *
 * <p>Bu servis Drive/rclone ile ilgilenmez (E2-06) ve Drive aynası
 * {@code expenses/faturalar}'a asla dokunmaz.
 */
public interface StorageService {

    /**
     * Bir fatura dosyasını depolar.
     *
     * @param invoiceId   dosyanın bağlandığı invoice id (loglama/bağlam için)
     * @param invoiceDate faturanın ÜZERİNDEKİ tarih — klasör ve ay adı bundan türetilir
     * @param serviceName slugify edilecek hizmet adı (ör. "Google Workspace")
     * @param providerId  duplicate kontrolü için provider id (null olabilir)
     * @param invoiceNo   duplicate kontrolü için fatura no (null olabilir)
     * @param originalFilename istemcinin gönderdiği orijinal ad (uzantı türetmek için)
     * @param content     dosya içeriği (akış tüketilir, çağıran kapatır)
     * @param fileType    beyan edilen dosya tipi (PDF/XML/STATEMENT/RECEIPT/OTHER)
     * @return depolanan dosyanın göreli yolu + hash + boyutu
     * @throws DuplicateFileException aynı (provider, invoice_no) ya da aynı SHA-256 zaten varsa
     * @throws StoragePathTraversalException üretilen yol kök dışına çıkarsa
     * @throws StorageException diğer I/O hataları
     */
    StoredFile store(Long invoiceId,
                     LocalDate invoiceDate,
                     String serviceName,
                     Long providerId,
                     String invoiceNo,
                     String originalFilename,
                     InputStream content,
                     FileType fileType);

    /**
     * Migrasyon (E2-03) için: bir kaynak dosyayı storage kökü ALTINA, göreli yolunu
     * AYNEN koruyarak kopyalar. Yeniden adlandırma / fatura-tarihi yeniden klasörleme
     * YAPMAZ — mevcut yerleşim olduğu gibi taşınır.
     *
     * <p>Drive aynası ({@code expenses/faturalar}) kaynaktır ve ASLA değiştirilmez; bu
     * metot yalnızca storage kökü içine YAZAR. {@code relativePath} kök altında çözülür,
     * yol-aşımına (path traversal) karşı korunur.
     *
     * <p>Idempotent: hedefte aynı içeriğe (SHA-256) sahip dosya zaten varsa yeniden
     * kopyalamaz; mevcut dosyanın metadata'sını döndürür.
     *
     * @param relativePath storage köküne göreli hedef yol (ör. {@code 2026-03/aws_mart.pdf})
     * @param content      kopyalanacak içerik (akış tüketilir, çağıran kapatır)
     * @return kopyalanan/var olan dosyanın göreli yolu + ad + SHA-256 + boyutu
     * @throws StoragePathTraversalException çözülen yol kök dışına çıkarsa
     * @throws StorageException I/O hatalarında
     */
    StoredFile copyPreservingPath(String relativePath, InputStream content);

    /** Fiziksel dosyayı {@code waiting/} altına taşır ve FileAsset.path'i günceller. */
    void moveToWaiting(Long fileId);

    /** Fiziksel dosyayı {@code trash/} altına taşır (silmez) ve FileAsset.path'i günceller. */
    void moveToTrash(Long fileId);

    /** İndirme için dosyayı Resource olarak yükler. */
    Resource loadAsResource(Long fileId);

    /**
     * İndirme için dosyayı Resource olarak yükler — zaten yüklenmiş bir {@link FileAsset}
     * kullanır (çağıran {@code findById} yaptıysa ikinci DB turunu önler).
     *
     * <p>Yol {@code asset.getFilePath()}'ten çözülür; yol-aşımına (path traversal) karşı
     * korunur. Fiziksel dosya eksik/okunamaz ise {@link StorageException} fırlatır.
     *
     * @param asset metadata kaydı (file_path buradan okunur)
     * @return okunabilir Resource
     * @throws StorageException fiziksel dosya yok/okunamıyorsa
     * @throws StoragePathTraversalException çözülen yol kök dışına çıkarsa
     */
    Resource loadAsResource(FileAsset asset);

    /**
     * Storage köküne göreli verilen fiziksel dosyayı en-iyi-çaba (best-effort) siler.
     * {@link #store} sonrası metadata persist'i başarısız olursa orphan dosyayı
     * temizlemek için kullanılır. Hata fırlatmaz; yalnızca silinip silinmediğini döndürür.
     *
     * @param relativePath {@link StoredFile#relativePath()} (kök altında olmalı)
     * @return dosya gerçekten silindiyse {@code true}; yoksa/başarısızsa {@code false}
     */
    boolean deletePhysical(String relativePath);
}
