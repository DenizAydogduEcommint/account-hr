package com.ecommint.accounthr.domain;

import java.math.BigDecimal;

import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * SERVICE-FIRST çekirdek varlık: ödenen tüm servislerin master listesi.
 * "Aktif + Aylık" servisler her ay bir expense satırı bekler; eksikse fatura eksik demektir.
 */
@Entity
@Table(name = "services")
public class Service extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_card_id")
    private Card defaultCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "using_team_id")
    private Team usingTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private Frequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_state", nullable = false)
    private ActiveState activeState;

    /** Operasyonel TOPLAM'a dahil edilmeyen bilgi-amaçlı satır (Multinet, sigorta...). */
    @Column(name = "informational", nullable = false)
    private boolean informational = false;

    @Column(name = "approx_amount_try", precision = 15, scale = 2)
    private BigDecimal approxAmountTry;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_source")
    private InvoiceSource invoiceSource;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * "Aktif Aylar" — Servisler master sheet'inden gelen virgüllü ay string'i
     * (ör. "2026-01, 2026-02, 2026-03"). Bilgi amaçlı; modelde service↔period
     * ilişkisi olmadığı için verbatim saklanır (E2-02).
     */
    @Column(name = "active_months", columnDefinition = "TEXT")
    private String activeMonths;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public Card getDefaultCard() {
        return defaultCard;
    }

    public void setDefaultCard(Card defaultCard) {
        this.defaultCard = defaultCard;
    }

    public Team getUsingTeam() {
        return usingTeam;
    }

    public void setUsingTeam(Team usingTeam) {
        this.usingTeam = usingTeam;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public ActiveState getActiveState() {
        return activeState;
    }

    public void setActiveState(ActiveState activeState) {
        this.activeState = activeState;
    }

    public boolean isInformational() {
        return informational;
    }

    public void setInformational(boolean informational) {
        this.informational = informational;
    }

    public BigDecimal getApproxAmountTry() {
        return approxAmountTry;
    }

    public void setApproxAmountTry(BigDecimal approxAmountTry) {
        this.approxAmountTry = approxAmountTry;
    }

    public InvoiceSource getInvoiceSource() {
        return invoiceSource;
    }

    public void setInvoiceSource(InvoiceSource invoiceSource) {
        this.invoiceSource = invoiceSource;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getActiveMonths() {
        return activeMonths;
    }

    public void setActiveMonths(String activeMonths) {
        this.activeMonths = activeMonths;
    }
}
