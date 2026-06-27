package com.ecommint.accounthr.dto.statement;

/**
 * confirm/discard sonucu (E4-01): etkilenen satır sayısı.
 *
 * @param batchRef  işlenen batch
 * @param confirmed durumu değişen (CONFIRMED/DISCARDED yapılan) satır sayısı.
 *                  Frontend (StatementConfirmResponse) bu alanı {@code confirmed}
 *                  adıyla okur; confirm ve discard aynı kaydı kullanır.
 */
public record StatementBatchActionResponse(
        String batchRef,
        int confirmed) {
}
