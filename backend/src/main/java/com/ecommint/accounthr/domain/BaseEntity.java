package com.ecommint.accounthr.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Ortak kimlik + zaman damgaları. Spring Data JPA auditing ile
 * createdAt/updatedAt otomatik doldurulur (@EnableJpaAuditing gerekli).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking discriminator (E1-DR-1). JPA increments this on every
     * UPDATE; a stale (detached) copy whose version no longer matches the row
     * triggers {@code OptimisticLockException} →
     * {@code ObjectOptimisticLockingFailureException}. No setter — JPA manages it.
     * DB column {@code version BIGINT NOT NULL DEFAULT 0} (V13). Existing rows → 0.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Optimistic-lock version (E1-DR-1). Read-only; JPA manages writes. */
    public Long getVersion() {
        return version;
    }
}
