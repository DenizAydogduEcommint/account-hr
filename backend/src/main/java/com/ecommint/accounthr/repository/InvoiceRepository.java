package com.ecommint.accounthr.repository;

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

    /** Durumu null kalan invoice sayısı (E2-01 garantisiyle 0 beklenir). */
    long countByStatusIsNull();
}
