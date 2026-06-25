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

    /**
     * E2-05 — Period bazlı ANA harcama TL toplamı (Excel TOPLAM mutabakatı için).
     *
     * <p>Mutabakat kuralı (CLAUDE.md: "TOPLAM sadece ana harcamalar"): Excel'in ana
     * {@code TOPLAM:} satırı bilgi-amaçlı (Multinet/Sigorta) kalemleri İÇERMEZ. Bu yüzden
     * DB toplamı da bilgi-amaçlı expense'leri HARİÇ tutar. Filtre {@code e.informational}
     * bayrağı üzerinden yapılır — bu bayrak E2-01'de Multinet/Sağlık Sigortası bölüm
     * başlığından sonra gelen satırlara {@code true} olarak set edilir (ve aynı satırların
     * invoice'u IGNORED olur). {@code informational} doğrudan domain bayrağı olduğundan
     * invoice varlığına BAĞLI DEĞİLDİR: invoice'u olmayan (ör. kısmi import) bir ana
     * expense yanlışlıkla dahil edilmez, bir bilgi-expense'i invoice'suz kalsa bile doğru
     * biçimde HARİÇ tutulur.
     *
     * <p>{@code amount_try} NULL olan satırlar (ör. Nisan, kısmi ay) toplama katkı vermez
     * (SUM NULL'ları atlar) ve hiç uygun satır yoksa SUM {@code null} döner.
     *
     * @return ana harcama {@code amount_try} toplamı; hiç uygun satır yoksa {@code null}.
     */
    @Query("SELECT SUM(e.amountTry) FROM Expense e "
            + "WHERE e.period.id = :periodId AND e.informational = false")
    java.math.BigDecimal sumMainAmountTryByPeriod(@org.springframework.data.repository.query.Param("periodId") Long periodId);

    /**
     * E2-05 — Period bazlı ANA harcama satır sayısı (Excel satır mutabakatı için).
     * Bilgi-amaçlı ({@code informational = true}, Multinet/Sigorta) expense'ler hariç sayılır.
     */
    @Query("SELECT COUNT(e) FROM Expense e "
            + "WHERE e.period.id = :periodId AND e.informational = false")
    long countMainExpensesByPeriod(@org.springframework.data.repository.query.Param("periodId") Long periodId);
}
