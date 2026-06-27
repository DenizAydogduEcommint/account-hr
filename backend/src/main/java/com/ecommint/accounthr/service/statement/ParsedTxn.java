package com.ecommint.accounthr.service.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ecommint.accounthr.domain.enums.Currency;

/**
 * Bir ekstre satırından parse edilmiş tek işlem (E4-01). Henüz {@code RawTransaction}
 * entity'sine dönüşmemiş, saf parse çıktısıdır (servis/karta bağlanmaz — o E4-02).
 *
 * <p>Tüm para alanları scale 2 BigDecimal; tarih + tutarlar nullable (parser her zaman
 * hepsini çıkaramayabilir). {@code rawText} orijinal satır metnidir (audit/debug).
 */
public record ParsedTxn(
        LocalDate transactionDate,
        String description,
        BigDecimal amount,
        Currency currency,
        BigDecimal amountTry,
        String rawText) {
}
