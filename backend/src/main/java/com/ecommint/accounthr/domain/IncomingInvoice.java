package com.ecommint.accounthr.domain;

import java.time.Instant;

import com.ecommint.accounthr.domain.enums.IncomingSource;
import com.ecommint.accounthr.domain.enums.IncomingStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Ham/gelen fatura (incoming invoice) — E5-02. Drive {@code faturalar/waiting/} (ileride mail)
 * kaynağından TOPLANMIŞ ama HENÜZ bir ekstre satırına/expense'e EŞLEŞMEMİŞ ham fatura dosyası.
 *
 * <p>Bu round YALNIZCA PULL + INGEST'tir: dosya Drive waiting/'den lokal storage'a KOPYALANIR
 * (rclone {@code copy}) ve her dosya için bir satır oluşturulur ({@link IncomingStatus#NEW}).
 * Eşleştirme + ay klasörüne taşıma + waiting silme E5-04'e ertelenmiştir; bu varlık o aşamaya
 * KÖPRÜdür. Drive'a ASLA push/delete yapılmaz.
 *
 * <p><b>Idempotency:</b> {@link #sourceRef} (Drive göreli yolu; ileride mail messageId)
 * {@code (source, source_ref)} ile UNIQUE'tir. Servis katmanı ayrıca {@link #sha256} ile
 * içerik-bazlı mükerrer tespiti yapar (aynı içerik farklı adla yeniden inse de yeni satır yok).
 */
@Entity
@Table(name = "incoming_invoices",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_incoming_source_ref", columnNames = { "source", "source_ref" }),
        indexes = {
                @Index(name = "ix_incoming_status", columnList = "status"),
                @Index(name = "ix_incoming_sha256", columnList = "sha256")
        })
public class IncomingInvoice extends BaseEntity {

    /** Toplama kaynağı (DRIVE_WAITING / MAIL). */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private IncomingSource source;

    /**
     * Kaynak referansı = idempotency anahtarı. DRIVE_WAITING için Drive waiting köküne göreli
     * dosya yolu; MAIL için (ileride) mail messageId. {@code (source, sourceRef)} UNIQUE.
     */
    @Column(name = "source_ref", nullable = false, length = 512)
    private String sourceRef;

    /** Orijinal dosya adı (Drive'daki ad). */
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    /** Pull edilen kopyanın STORAGE_ROOT'a göreli yolu (storage servisi üzerinden yazıldı). */
    @Column(name = "stored_path", nullable = false, length = 1024)
    private String storedPath;

    /** İçeriğin SHA-256 hex digesti (64 karakter). İçerik-bazlı mükerrer tespiti için. */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /** Bu ham faturanın toplandığı (pull edildiği) an. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IncomingStatus status = IncomingStatus.NEW;

    /** Serbest not (nullable). */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public IncomingSource getSource() {
        return source;
    }

    public void setSource(IncomingSource source) {
        this.source = source;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public IncomingStatus getStatus() {
        return status;
    }

    public void setStatus(IncomingStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
