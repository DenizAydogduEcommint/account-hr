package com.ecommint.accounthr.dto.importer;

import java.util.List;

/**
 * E2-02 — "Servisler" master sheet import işleminin özet raporu.
 *
 * <p>Sayım alanları (created/updated/contactsCreated/providersCreated/informationalCount)
 * + iki eşleşmeyen listesi:
 * <ul>
 *   <li>{@code masterWithoutExpenses}: master sheet'te var ama hiç expense satırı olmayan
 *       servis isimleri (ör. pasif/inactive servisler) — fatura beklenmeyenler.</li>
 *   <li>{@code expensesWithoutMaster}: expense'lerde geçen ama bu import'taki master
 *       sheet'te bulunmayan servis isimleri — master'a eklenmesi gerekenler.</li>
 * </ul>
 * Bu iki liste DOD'taki "eşleşmeyenler raporlanıyor" kriterini karşılar.
 */
public record ServiceImportSummary(
        int rowsRead,
        int created,
        int updated,
        int skipped,
        int contactsCreated,
        int providersCreated,
        int informationalCount,
        List<String> masterWithoutExpenses,
        List<String> expensesWithoutMaster) {
}
