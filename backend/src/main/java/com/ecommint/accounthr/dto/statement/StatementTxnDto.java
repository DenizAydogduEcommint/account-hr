package com.ecommint.accounthr.dto.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ecommint.accounthr.domain.RawTransaction;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.RawTxnStatus;

/**
 * Önizleme/yeniden-önizleme yanıtında tek ham işlem (E4-01). Persist edilmiş
 * {@link RawTransaction}'tan türetilir.
 */
public record StatementTxnDto(
        Long id,
        LocalDate transactionDate,
        String description,
        BigDecimal amount,
        Currency currency,
        BigDecimal amountTry,
        RawTxnStatus status,
        boolean matched,
        String parseWarning,
        String rawText) {

    public static StatementTxnDto from(RawTransaction t) {
        return new StatementTxnDto(
                t.getId(),
                t.getTransactionDate(),
                t.getDescription(),
                t.getAmount(),
                t.getCurrency(),
                t.getAmountTry(),
                t.getStatus(),
                t.isMatched(),
                t.getParseWarning(),
                t.getRawText());
    }
}
