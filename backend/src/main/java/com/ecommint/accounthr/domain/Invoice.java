package com.ecommint.accounthr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Bir expense'e bağlı fatura. expense → invoices 1:N (iade / invoice+receipt / duplicate).
 */
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @Column(name = "invoice_no")
    private String invoiceNo;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency")
    private Currency currency;

    @Column(name = "is_refund", nullable = false)
    private boolean refund = false;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * E3-11 — KDV (VAT) oranı yüzde olarak (ör. 20.00 = %20). Opsiyonel: oran
     * girilmediğinde null kalır ve {@link #kdvAmountTry}/{@link #netAmountTry} de null
     * olur (mevcut faturalar etkilenmez). KDV faturaya (belgeye) aittir, harcamaya değil.
     */
    @Column(name = "kdv_rate", precision = 5, scale = 2)
    private BigDecimal kdvRate;

    /** E3-11 — KDV tutarı (TL), brüt TL'den türetilir: {@code gross - net}. Oran yoksa null. */
    @Column(name = "kdv_amount_try", precision = 15, scale = 2)
    private BigDecimal kdvAmountTry;

    /** E3-11 — Matrah / net taban (TL): {@code gross / (1 + rate/100)}. Oran yoksa null. */
    @Column(name = "net_amount_try", precision = 15, scale = 2)
    private BigDecimal netAmountTry;

    public Expense getExpense() {
        return expense;
    }

    public void setExpense(Expense expense) {
        this.expense = expense;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
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

    public boolean isRefund() {
        return refund;
    }

    public void setRefund(boolean refund) {
        this.refund = refund;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public BigDecimal getKdvRate() {
        return kdvRate;
    }

    public void setKdvRate(BigDecimal kdvRate) {
        this.kdvRate = kdvRate;
    }

    public BigDecimal getKdvAmountTry() {
        return kdvAmountTry;
    }

    public void setKdvAmountTry(BigDecimal kdvAmountTry) {
        this.kdvAmountTry = kdvAmountTry;
    }

    public BigDecimal getNetAmountTry() {
        return netAmountTry;
    }

    public void setNetAmountTry(BigDecimal netAmountTry) {
        this.netAmountTry = netAmountTry;
    }
}
