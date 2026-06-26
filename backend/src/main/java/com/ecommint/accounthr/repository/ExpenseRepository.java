package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.ecommint.accounthr.domain.Expense;

public interface ExpenseRepository
        extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByPeriodId(Long periodId);

    /**
     * E3-03 — Bir dönemin bilgi-amaçlı ({@code informational=true}) harcamaları
     * (Multinet / sigorta / vergi). Aylık harcamalar ekranında ayrı bölümde, operasyonel
     * toplama dahil edilmeden gösterilir. Servis/sağlayıcı eager-fetch'lenir (DTO eşlemesi
     * için lazy session bağımlılığını azaltır). Tarihe göre sıralı.
     */
    @Query("SELECT e FROM Expense e "
            + "LEFT JOIN FETCH e.service s "
            + "LEFT JOIN FETCH s.provider "
            + "LEFT JOIN FETCH e.card "
            + "LEFT JOIN FETCH e.usingTeam "
            + "WHERE e.period.id = :periodId AND e.informational = true "
            + "ORDER BY e.transactionDate ASC NULLS LAST, e.id ASC")
    List<Expense> findInformationalByPeriod(
            @org.springframework.data.repository.query.Param("periodId") Long periodId);

    /**
     * E3-03 — Dönemin bilgi-amaçlı ({@code informational=true}) harcama TL alt toplamı.
     * Operasyonel toplama dahil DEĞİLDİR; ekranda ayrı bölüm alt toplamı olarak gösterilir.
     * {@code amount_try} NULL satırları katkı vermez (SUM NULL'ları atlar); hiç satır yoksa
     * {@code null} döner.
     */
    @Query("SELECT SUM(e.amountTry) FROM Expense e "
            + "WHERE e.period.id = :periodId AND e.informational = true")
    java.math.BigDecimal sumInformationalAmountTryByPeriod(
            @org.springframework.data.repository.query.Param("periodId") Long periodId);

    /**
     * E3-03 N+1 fix — Sayfa içeriğinin expense ID'leri için service/provider/card/usingTeam
     * eager-fetch'li (tek sorgu) yeniden çekim. ANA sayfa yolu önce {@code findAll(spec,
     * pageable)} ile DOĞRU sayfalama + count üretir (koleksiyon FETCH yok → count bozulmaz),
     * sonra bu sorgu o sayfanın ID'lerini TEK sorguda ilişkileriyle getirir; böylece
     * {@code toRow()} içindeki lazy erişimler (service, provider, card, usingTeam) per-row
     * sorgu üretmez. Yalnızca {@code *ToOne} ilişkileri fetch edilir (koleksiyon değil),
     * IN listesi sınırlı (sayfa boyutu kadar) olduğundan sayfalama bozulmaz. Sıra çağıran
     * tarafça sayfa sırasına göre yeniden kurulur (IN sonucu sıralı gelmeyebilir).
     */
    @Query("SELECT e FROM Expense e "
            + "LEFT JOIN FETCH e.service s "
            + "LEFT JOIN FETCH s.provider "
            + "LEFT JOIN FETCH e.card "
            + "LEFT JOIN FETCH e.usingTeam "
            + "WHERE e.id IN :ids")
    List<Expense> findByIdInFetchingRefs(
            @org.springframework.data.repository.query.Param("ids") List<Long> ids);

    /** "Bu servisin bu ay bir satırı var mı?" — eksik fatura çapraz doğrulaması. */
    boolean existsByServiceIdAndPeriodId(Long serviceId, Long periodId);

    /**
     * E3-04 — Verilen dönemde durumu {@code FOUND} ya da {@code E_INVOICE} olan EN AZ BİR
     * harcaması bulunan servislerin distinct ID'leri. "Faturası bulunmuş" servis tanımıdır:
     * çapraz doğrulamada bu kümedeki servisler eksik DEĞİLDİR. Tek sorgu (per-servis N+1 yok);
     * kontrol kümesi ile küme-farkı alınarak eksikler bulunur.
     *
     * <p>"Var" tanımı satır durumu değil INVOICE durumu üzerinden kurulur: aynı expense'in
     * birden çok invoice'u olabilir (iade / invoice+receipt); herhangi biri FOUND/E_INVOICE
     * ise servis o ay için "bulundu" sayılır. Bekleniyor ({@code EXPECTED}) ya da hiç invoice
     * olmaması bu kümeye katkı vermez → servis eksik kalır.
     *
     * <p>Bilgi-amaçlı ({@code informational=true}) expense'ler HARİÇ tutulur: böyle bir
     * harcamanın FOUND invoice'u olsa bile servisi eksik listesinden yanlışlıkla düşürmez
     * (dashboard {@code missingCount}'ı çarpıtmaz). Çapraz doğrulama yalnızca operasyonel
     * (ana) harcamalar üzerinden yapılır.
     */
    @Query("SELECT DISTINCT e.service.id FROM Expense e JOIN Invoice i ON i.expense = e "
            + "WHERE e.period.id = :periodId "
            + "AND e.informational = false "
            + "AND i.status IN (com.ecommint.accounthr.domain.enums.InvoiceStatus.FOUND, "
            + "com.ecommint.accounthr.domain.enums.InvoiceStatus.E_INVOICE)")
    List<Long> findServiceIdsWithFoundInvoiceInPeriod(
            @org.springframework.data.repository.query.Param("periodId") Long periodId);

    /**
     * E3-04 — Verilen servisler için "en son görüldüğü ay" eşlemesi: her servisin herhangi bir
     * harcamasında geçen EN BÜYÜK period kodu (YYYY-MM lexicografik = kronolojik). Tek toplu
     * sorgu (N+1 yok). {@code [serviceId, maxPeriodCode]} satırları döner; hiç harcaması olmayan
     * servis sonuçta yer almaz (çağıran tarafça null kabul edilir).
     *
     * <p>E3 deep-review #4: yalnızca OPERASYONEL ({@code informational=false}) harcamalar
     * sayılır. "Bulundu" kapısı ({@code findServiceIdsWithFoundInvoiceInPeriod}) da
     * {@code informational=false} kullandığından, "en son görülen ay" da bilgi-amaçlı
     * (ignored/Multinet/sigorta) aktiviteyi YANSITMAMALI — aksi halde tutarsız olurdu.
     */
    @Query("SELECT e.service.id, MAX(e.period.code) FROM Expense e "
            + "WHERE e.service.id IN :serviceIds AND e.informational = false "
            + "GROUP BY e.service.id")
    List<Object[]> findLastSeenMonthByServiceIds(
            @org.springframework.data.repository.query.Param("serviceIds")
            java.util.Collection<Long> serviceIds);

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
