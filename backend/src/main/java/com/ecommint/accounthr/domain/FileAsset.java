package com.ecommint.accounthr.domain;

import java.time.LocalDateTime;

import com.ecommint.accounthr.domain.enums.FileType;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/**
 * Fatura dosyası metadata + path. invoice → files 1:N (pdf/xml/statement).
 * Tablo adı `files`. Sadece createdAt taşır (updatedAt yok) — bu yüzden BaseEntity'den türemez.
 *
 * <p><b>uq_files_invoice_sha256 — KISMİ tekil index semantiği (E2-DR-1).</b> Postgres'te
 * (V15) bu, {@code WHERE sha256 IS NOT NULL} koşullu KISMİ bir unique index'tir: yani
 * {@code sha256} NULL olan satırlar DB düzeyinde tekilleştirilMEZ (null-sha kayıtlar
 * birbirini çakışma saymaz) — null-sha içerik-dedup'ı UYGULAMA katmanı yapar
 * ({@code InvoiceFileImportService}). Aşağıdaki {@code @UniqueConstraint} annotation'ı yalnızca
 * H2 TEST şemasını (ddl-auto=create-drop) sürer ve Postgres kısmi index'inin bir ÜST KÜMESİdir
 * (H2 unique index'i birden çok NULL'a izin verdiğinden pratikte null-sha satırları yine
 * çakışmaz; dolu sha için davranış birebir aynıdır). İki ortam da fatura-içi-dedup yarışını
 * dolu-sha için DB düzeyinde kapatır.
 * Tekillik artık FATURA başınadır: AYNI faturada aynı içerik (sha256) iki kez bulunamaz, ama
 * AYNI içerik FARKLI faturalara birer satırla bağlanabilir (byte-aynı dosya iki meşru faturaya
 * ait olabilir; fizik dosya bir kez saklanır, ikinci satır aynı path'i paylaşır). Dolu sha256
 * fatura başına tekildir; NULL sha256'lar serbesttir.
 */
@Entity
@Table(name = "files", uniqueConstraints = @UniqueConstraint(
        name = "uq_files_invoice_sha256", columnNames = {"invoice_id", "sha256"}))
@EntityListeners(AuditingEntityListener.class)
public class FileAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Dosyanın bağlandığı invoice. Migrasyon modunda (E2-03) eşleşmeyen / trash'teki
     * dosyalar invoice'a bağlanmaz → {@code null} olabilir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** İçeriğin SHA-256 hex digesti (64 karakter) — duplicate tespiti + bütünlük (E1-04). */
    @Column(name = "sha256", length = 64)
    private String sha256;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    /** Optimistic locking (E1-DR-1) — DB column version BIGINT NOT NULL DEFAULT 0 (V13). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    /** Optimistic-lock version (E1-DR-1). Read-only; JPA manages writes. */
    public Long getVersion() {
        return version;
    }

    public AppUser getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(AppUser uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
