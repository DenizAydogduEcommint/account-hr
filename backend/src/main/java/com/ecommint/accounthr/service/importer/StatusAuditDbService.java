package com.ecommint.accounthr.service.importer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.importer.StatusAuditSummary;
import com.ecommint.accounthr.dto.importer.StatusAuditSummary.FileStatusInconsistency;
import com.ecommint.accounthr.dto.importer.StatusAuditSummary.TextColorMismatch;
import com.ecommint.accounthr.repository.InvoiceRepository;

/**
 * E2-04 — Durum/renk denetiminin B BÖLÜMÜ (DB dosya/durum tutarlılığı).
 *
 * <p>Eskiden {@link StatusAuditService} içinde {@code @Lazy} öz-enjeksiyonla sarılan
 * {@code @Transactional} mantığı buraya ayrı bir {@code @Service} olarak çıkarıldı.
 * Böylece {@link StatusAuditService} bu sınıfı normal constructor enjeksiyonuyla alır;
 * proxy çözümleme kırılganlığı (öz-çağrının transaction'ı atlaması, autofix=true
 * yazımlarının transaction sınırı olmadan koşma riski) ortadan kalkar.
 *
 * <p>E2-03'te en az bir {@code FileAsset} bağlanmış ama hâlâ {@link InvoiceStatus#EXPECTED}
 * kalan invoice'lar tutarsızdır. {@code autofix} açıksa durum {@link InvoiceStatus#FOUND}'a
 * (note/durumda e-Fatura işareti varsa {@link InvoiceStatus#E_INVOICE}) çekilir.
 */
@Service
public class StatusAuditDbService {

    private static final Logger log = LoggerFactory.getLogger(StatusAuditDbService.class);

    private final InvoiceRepository invoiceRepository;

    public StatusAuditDbService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * B bölümü: dosyası olduğu hâlde EXPECTED kalan invoice'ları raporlar; {@code autofix}
     * açıksa FOUND/E_INVOICE'a çeker. {@code @Transactional} — DB okuma + autofix mutasyon
     * döngüsünü tek bir transaction sınırında sarar (kısmi-fix riski yok).
     *
     * <p>Önemli: durum dağılımı autofix mutasyonlarından ÖNCE alınır ki rapordaki
     * {@code statusDistribution} ile {@code fileStatusInconsistent} aynı (fix-öncesi)
     * anlık görüntüye dayansın (iç tutarlılık). Düzeltme sonuçları satır-başı
     * {@code FileStatusInconsistency.newStatus} alanında raporlanır.
     */
    @Transactional
    public DbAuditResult auditFileStatus(boolean autofix) {
        // Dağılım anlık görüntüsü: autofix'ten ÖNCE (fix-öncesi tutarlı rapor).
        Map<InvoiceStatus, Long> distribution = statusDistribution();

        List<FileStatusInconsistency> inconsistencies = new ArrayList<>();
        int fixed = 0;
        // Tek sorgu: EXPECTED + dosya sayısı (N+1 yok, in-loop sorgu yok).
        for (Object[] row : invoiceRepository.findWithFileCountByStatus(InvoiceStatus.EXPECTED)) {
            Invoice invoice = (Invoice) row[0];
            int fileCount = ((Number) row[1]).intValue();
            InvoiceStatus oldStatus = invoice.getStatus();
            InvoiceStatus newStatus = oldStatus;
            boolean didFix = false;
            if (autofix) {
                newStatus = inferFoundStatus(invoice);
                invoice.setStatus(newStatus);
                invoiceRepository.save(invoice);
                didFix = true;
                fixed++;
            }
            inconsistencies.add(new FileStatusInconsistency(
                    invoice.getId(), fileCount, oldStatus, newStatus, didFix));
        }

        // Doğrulama: tanımsız durum (E2-01 garantisiyle 0).
        int undefinedStatus = (int) invoiceRepository.countByStatusIsNull();
        if (undefinedStatus > 0) {
            log.warn("E2-04: {} invoice durumu NULL — E2-01 garantisi ihlal edildi.", undefinedStatus);
        }

        return new DbAuditResult(distribution, inconsistencies, fixed, undefinedStatus);
    }

    /** B bölümünün DB sonuçları; A bölümüyle birleşip {@link StatusAuditSummary}'ye dönüşür. */
    record DbAuditResult(
            Map<InvoiceStatus, Long> distribution,
            List<FileStatusInconsistency> inconsistencies,
            int fixed,
            int undefinedStatus) {

        StatusAuditSummary toSummary(List<TextColorMismatch> mismatches) {
            return new StatusAuditSummary(
                    distribution,
                    mismatches.size(),
                    inconsistencies.size(),
                    fixed,
                    undefinedStatus,
                    mismatches,
                    inconsistencies);
        }
    }

    /**
     * Dosyası bulunan EXPECTED invoice'ın düzeltilmiş durumu. Basit kural: kaynak/note
     * e-Fatura işaret ediyorsa {@link InvoiceStatus#E_INVOICE}, aksi halde
     * {@link InvoiceStatus#FOUND}.
     */
    private InvoiceStatus inferFoundStatus(Invoice invoice) {
        String note = invoice.getNote();
        if (note != null) {
            String l = note.toLowerCase(Locale.forLanguageTag("tr"));
            if (l.contains("e-fatura") || l.contains("efatura")) {
                return InvoiceStatus.E_INVOICE;
            }
        }
        return InvoiceStatus.FOUND;
    }

    private Map<InvoiceStatus, Long> statusDistribution() {
        Map<InvoiceStatus, Long> distribution = new EnumMap<>(InvoiceStatus.class);
        for (Object[] row : invoiceRepository.countGroupByStatus()) {
            InvoiceStatus status = (InvoiceStatus) row[0];
            Long count = ((Number) row[1]).longValue();
            if (status != null) {
                distribution.put(status, count);
            }
        }
        return distribution;
    }
}
