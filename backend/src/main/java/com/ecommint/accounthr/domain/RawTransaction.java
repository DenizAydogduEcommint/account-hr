package com.ecommint.accounthr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.RawTxnStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Ham işlem (E4-01) — kart ekstresinden / dönem-içi hareket dökümünden parse edilmiş
 * TEK bir satır. HENÜZ bir servise/expense'e eşleşmemiştir; eşleştirme (matching) E4-02'nin
 * işidir ({@link #matched} bayrağını E4-02 {@code true} yapacak).
 *
 * <p>Akış: upload → parse → {@code PENDING} satırlar yazılır → kullanıcı önizler → confirm
 * ({@code CONFIRMED}) veya discard ({@code DISCARDED}). {@link #sourceFileSha256} idempotency
 * anahtarıdır: aynı dosya + kart + dönem için CONFIRMED bir batch zaten varsa tekrar parse
 * edilmez (mükerrer satır oluşmaz).
 *
 * <p>Para modeli (CLAUDE.md): {@code amount} (orijinal döviz) + {@code currency} +
 * {@code amountTry} (ekstre TL). Her ikisi de scale 2, nullable (TL-only ya da döviz-only
 * satırlar olabilir). Tarih de nullable (parser her zaman tarih çıkaramayabilir).
 */
@Entity
@Table(name = "raw_transactions", indexes = {
        @Index(name = "ix_raw_txn_sha256", columnList = "source_file_sha256"),
        @Index(name = "ix_raw_txn_period", columnList = "period_id"),
        @Index(name = "ix_raw_txn_status", columnList = "status")
})
public class RawTransaction extends BaseEntity {

    /** İşlemin ait olduğu kart (last4 ile çözülür). Upload kart zorunlu olduğundan dolu. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private Card card;

    /** İşlemin ait olduğu dönem (upload {@code month} YYYY-MM'den find-or-create). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private Period period;

    /** Ekstredeki işlem tarihi. Nullable — parser tarihi her zaman çıkaramayabilir. */
    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    /** İşyeri / açıklama (ekstre satır metni). */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Orijinal (döviz) tutar. Nullable. */
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 8)
    private Currency currency = Currency.TRY;

    /** Ekstredeki TL karşılığı. Nullable. */
    @Column(name = "amount_try", precision = 15, scale = 2)
    private BigDecimal amountTry;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RawTxnStatus status = RawTxnStatus.PENDING;

    /** Yüklenen ekstre dosyasının SHA-256'sı — batch anahtarı + idempotency. */
    @Column(name = "source_file_sha256", nullable = false, length = 64)
    private String sourceFileSha256;

    @Column(name = "source_file_name")
    private String sourceFileName;

    /** Orijinal satır metni (denetim/hata ayıklama için). */
    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    /** Parser uyarısı (ör. placeholder uyarısı). Nullable. */
    @Column(name = "parse_warning", columnDefinition = "TEXT")
    private String parseWarning;

    /** E4-02 eşleştirme sonrası {@code true} olur. Varsayılan {@code false}. */
    @Column(name = "matched", nullable = false)
    private boolean matched = false;

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public Period getPeriod() {
        return period;
    }

    public void setPeriod(Period period) {
        this.period = period;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public RawTxnStatus getStatus() {
        return status;
    }

    public void setStatus(RawTxnStatus status) {
        this.status = status;
    }

    public String getSourceFileSha256() {
        return sourceFileSha256;
    }

    public void setSourceFileSha256(String sourceFileSha256) {
        this.sourceFileSha256 = sourceFileSha256;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getParseWarning() {
        return parseWarning;
    }

    public void setParseWarning(String parseWarning) {
        this.parseWarning = parseWarning;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }
}
