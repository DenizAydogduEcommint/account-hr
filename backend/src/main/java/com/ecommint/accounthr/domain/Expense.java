package com.ecommint.accounthr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ecommint.accounthr.domain.enums.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Kart işlemi (transaction) = ay sheet'indeki bir satır.
 * service_id ZORUNLU FK — servis↔expense eşleşme anahtarı (isim bazlı DEĞİL).
 * Para modeli (MVP): amount (orijinal) + currency + amountTry (ekstre TL). Kur tarihi TUTULMAZ.
 */
@Entity
@Table(name = "expenses")
public class Expense extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private Period period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    /**
     * Kart ekstresindeki işlem tarihi. NULLABLE: "Bekleniyor" satırlarında (henüz
     * çekilmemiş) tarih boş olabilir (E2-01). NOT NULL kısıtı V6 ile kaldırıldı.
     */
    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    /**
     * Orijinal (döviz) tutar. NULLABLE: TL ödemelerinde Tutar boş kalıp yalnızca
     * {@link #amountTry} dolu olabilir (E2-01 Excel importer). NOT NULL kısıtı V6
     * migration ile kaldırıldı.
     */
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    /** Kart ekstresindeki TL karşılığı. */
    @Column(name = "amount_try", precision = 15, scale = 2)
    private BigDecimal amountTry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "using_team_id")
    private Team usingTeam;

    /** Operasyonel TOPLAM'a dahil değil (Multinet, sigorta...). */
    @Column(name = "informational", nullable = false)
    private boolean informational = false;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    /**
     * Excel importer'ın bir kaynak satırından ürettiği stabil SHA-256 (E2-01).
     * Idempotency anahtarı: tekrar import'ta aynı hash varsa satır atlanır.
     * Import dışı oluşan expense'lerde null.
     */
    @Column(name = "source_row_hash", length = 64)
    private String sourceRowHash;

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Period getPeriod() {
        return period;
    }

    public void setPeriod(Period period) {
        this.period = period;
    }

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public BigDecimal getAmountTry() {
        return amountTry;
    }

    public void setAmountTry(BigDecimal amountTry) {
        this.amountTry = amountTry;
    }

    public Team getUsingTeam() {
        return usingTeam;
    }

    public void setUsingTeam(Team usingTeam) {
        this.usingTeam = usingTeam;
    }

    public boolean isInformational() {
        return informational;
    }

    public void setInformational(boolean informational) {
        this.informational = informational;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getSourceRowHash() {
        return sourceRowHash;
    }

    public void setSourceRowHash(String sourceRowHash) {
        this.sourceRowHash = sourceRowHash;
    }
}
