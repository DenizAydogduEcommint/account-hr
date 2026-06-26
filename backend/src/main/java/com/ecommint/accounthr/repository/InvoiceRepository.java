package com.ecommint.accounthr.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByExpenseId(Long expenseId);

    /**
     * E3-07 — Bir expense'in TEMSİLCİ (en güncel = en yüksek id'li) invoice'u. Aylık
     * harcamalar ekranında satırın GÖSTERİLEN durumu bu invoice'tan gelir
     * ({@code ExpenseQueryService.representativeInvoices} ile AYNI tanım: id ASC sırasında
     * "son kazanır" = max-id). Elle durum değişimi (PATCH) bu invoice'un durumunu günceller.
     * Hiç invoice yoksa boş döner.
     */
    java.util.Optional<Invoice> findFirstByExpenseIdOrderByIdDesc(Long expenseId);

    /**
     * E3-03 — Verilen expense'lere bağlı TÜM invoice'lar (toplu, N+1'siz). Aylık harcamalar
     * ekranı her satır için temsilci (en güncel = en yüksek id'li) invoice'un durum + notunu
     * gösterir; seçim servis katmanında {@code id}'ye göre yapılır. {@code id ASC} sıralı gelir
     * ki "son kazanır" indirgemesi deterministik olsun. Boş koleksiyon → boş liste.
     */
    @Query("SELECT i FROM Invoice i WHERE i.expense.id IN :expenseIds ORDER BY i.id ASC")
    List<Invoice> findByExpenseIdIn(@Param("expenseIds") Collection<Long> expenseIds);

    /**
     * "Fatura Notu" (note) alanı dolu olan tüm invoice'lar. E2-03 eşleme indeksini
     * (note-path → invoice) bunlardan kurar.
     */
    List<Invoice> findByNoteIsNotNull();

    /**
     * E2-04 — Belirtilen durumda olup EN AZ BİR {@code FileAsset} ({@code files} tablosu)
     * bağlanmış invoice'ları, bağlı dosya sayısıyla birlikte döner. "Dosya bulundu ama
     * Bekleniyor" tutarsızlığını {@code status = EXPECTED} ile sorgulamak için kullanılır.
     *
     * <p>Tek sorguda invoice + dosya sayısı gelir (N+1 yok). {@code Invoice}'ta
     * {@code @OneToMany files} eşlemesi YOK; bu yüzden Hibernate 6'nın ad-hoc
     * {@code JOIN ... ON} formuyla açık INNER JOIN kurulur ({@code f.invoice = i}).
     * Bu, eski {@code FROM Invoice i, FileAsset f} örtük çapraz-join (Cartesian)
     * formunun yerine geçer ve Hibernate'in gerçek bir SQL {@code INNER JOIN}
     * üretmesini sağlar. {@code GROUP BY i} ile her invoice tek satır olur ve
     * {@code COUNT(f)} doğal olarak ≥ 1'dir. Sonuç {@code [Invoice, Long fileCount]}
     * satırları olarak gelir.
     */
    @Query("SELECT i, COUNT(f) FROM Invoice i JOIN FileAsset f ON f.invoice = i "
            + "WHERE i.status = :status "
            + "GROUP BY i")
    List<Object[]> findWithFileCountByStatus(@Param("status") InvoiceStatus status);

    /** Durum→adet dağılımı (DB). {@code [InvoiceStatus, Long]} satırları döner. */
    @Query("SELECT i.status, COUNT(i) FROM Invoice i GROUP BY i.status")
    List<Object[]> countGroupByStatus();

    /**
     * E3-01 — TEK bir dönem (period) için durum→adet dağılımı. Invoice'un period'a
     * doğrudan FK'i yoktur; ilişki {@code Invoice → Expense → Period} üzerinden kurulur
     * ({@code i.expense.period.id}). Yalnızca verilen periyoda ait invoice'lar gruplanır.
     * {@code [InvoiceStatus, Long]} satırları döner; o periyotta hiç o durumdan yoksa
     * o durum sonuçta YER ALMAZ (eksikler servis katmanında 0'a tamamlanır).
     */
    @Query("SELECT i.status, COUNT(i) FROM Invoice i WHERE i.expense.period.id = :periodId GROUP BY i.status")
    List<Object[]> countGroupByStatusForPeriod(@Param("periodId") Long periodId);

    /** Durumu null kalan invoice sayısı (E2-01 garantisiyle 0 beklenir). */
    long countByStatusIsNull();
}
