package com.ecommint.accounthr.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.IncomingInvoice;
import com.ecommint.accounthr.domain.enums.IncomingSource;
import com.ecommint.accounthr.domain.enums.IncomingStatus;

/**
 * {@link IncomingInvoice} (E5-02) deposu. Idempotency sorguları: (source, sourceRef) ve sha256.
 */
public interface IncomingInvoiceRepository extends JpaRepository<IncomingInvoice, Long> {

    /** Idempotency: aynı kaynak + kaynak referansı zaten var mı? */
    boolean existsBySourceAndSourceRef(IncomingSource source, String sourceRef);

    /** İçerik-bazlı mükerrer: aynı sha256'ya sahip bir ham fatura zaten var mı? */
    boolean existsBySha256(String sha256);

    /** Tüm ham faturalar, en yeni önce (createdAt desc). */
    List<IncomingInvoice> findAllByOrderByCreatedAtDescIdDesc();

    /** Belirli durumdaki ham faturalar, en yeni önce. */
    List<IncomingInvoice> findByStatusOrderByCreatedAtDescIdDesc(IncomingStatus status);

    /** (source, sourceRef) ile tekil arama (idempotency mevcut satırı döndürmek için). */
    Optional<IncomingInvoice> findBySourceAndSourceRef(IncomingSource source, String sourceRef);
}
