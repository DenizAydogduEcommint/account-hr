package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ecommint.accounthr.domain.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByPeriodId(Long periodId);

    /** "Bu servisin bu ay bir satırı var mı?" — eksik fatura çapraz doğrulaması. */
    boolean existsByServiceIdAndPeriodId(Long serviceId, Long periodId);

    /** Importer idempotency: aynı kaynak satır hash'i daha önce eklenmiş mi? (E2-01) */
    boolean existsBySourceRowHash(String sourceRowHash);

    /**
     * Expense'lerin referans verdiği distinct servis isimleri (E2-02 eşleşmeyen raporu).
     * "expense'lerde geçen ama master sheet'te olmayan" servisleri hesaplamak için.
     */
    @Query("SELECT DISTINCT e.service.name FROM Expense e")
    List<String> findDistinctServiceNames();

    /**
     * Hiç expense'i olmayan servislerin ID'leri (E2-02 eşleşmeyen raporu).
     * "master'da var ama hiç expense satırı yok" servisleri tespit için.
     */
    @Query("SELECT s.id FROM Service s WHERE NOT EXISTS "
            + "(SELECT 1 FROM Expense e WHERE e.service = s)")
    List<Long> findServiceIdsWithoutExpenses();
}
