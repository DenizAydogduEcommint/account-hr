package com.ecommint.accounthr.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

import jakarta.persistence.EntityManager;

/**
 * E1-DR-1/DR-2/DR-5 + E2-DR-1 teknik-borç kapanışının DB-düzeyi (H2, ddl-auto=create-drop)
 * doğrulaması. Flyway testlerde kapalı olduğundan şema entity'lerden üretilir; bu
 * yüzden @Version kolonları, services tekil kısıtı ve files {@code (invoice_id, sha256)}
 * bileşik tekil kısıtı (E2-DR-1; eski global {@code uq_files_sha256} yerine) burada test
 * şemasında da etkindir.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class OptimisticLockingAndUniquenessJpaTest {

    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private EntityManager entityManager;

    // --- E1-DR-1: optimistic locking ---------------------------------------

    @Test
    void version_isManagedByJpa_startsAtZeroAndIncrementsOnUpdate() {
        Provider provider = new Provider();
        provider.setName("VersionedProvider");
        provider = providerRepository.save(provider);
        entityManager.flush();

        assertThat(provider.getVersion()).isZero();

        provider.setName("VersionedProvider-renamed");
        provider = providerRepository.saveAndFlush(provider);

        assertThat(provider.getVersion()).isEqualTo(1L);
    }

    @Test
    void staleUpdate_throwsObjectOptimisticLockingFailure() {
        Provider provider = new Provider();
        provider.setName("StaleTarget");
        provider = providerRepository.save(provider);
        entityManager.flush();
        Long id = provider.getId();

        // Detached "stale" snapshot at version 0.
        entityManager.detach(provider);

        // A concurrent actor loads the SAME row, updates it → DB version becomes 1.
        Provider fresh = providerRepository.findById(id).orElseThrow();
        fresh.setName("StaleTarget-updated-by-other");
        providerRepository.saveAndFlush(fresh);
        entityManager.clear();

        // Now re-applying the stale (version 0) copy must fail: its version no longer
        // matches the persisted row (now version 1). Going through the repository
        // exercises Spring Data's exception translation (the path the production
        // GlobalExceptionHandler relies on), yielding ObjectOptimisticLockingFailureException.
        final Provider stale = provider;
        stale.setName("StaleTarget-lost-update");
        assertThatThrownBy(() -> providerRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // --- E1-DR-2: services (name, provider_id) UNIQUE ----------------------

    @Test
    void duplicateServiceNameProvider_violatesUniqueConstraint() {
        Provider provider = new Provider();
        provider.setName("DupProvider");
        provider = providerRepository.save(provider);

        Service first = newService("Claude AI", provider);
        serviceRepository.saveAndFlush(first);

        Service duplicate = newService("Claude AI", provider);

        assertThatThrownBy(() -> serviceRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameServiceNameDifferentProvider_isAllowed() {
        Provider p1 = new Provider();
        p1.setName("ProviderOne");
        p1 = providerRepository.save(p1);

        Provider p2 = new Provider();
        p2.setName("ProviderTwo");
        p2 = providerRepository.save(p2);

        serviceRepository.saveAndFlush(newService("Shared Name", p1));
        // Same name, different provider → constraint is on the PAIR, so this is fine.
        serviceRepository.saveAndFlush(newService("Shared Name", p2));

        assertThat(serviceRepository.count()).isEqualTo(2);
    }

    // --- E2-DR-1: files (invoice_id, sha256) composite-unique in test (H2) schema ----

    @Test
    void sameInvoiceDuplicateSha256_violatesUniqueConstraint() {
        // E2-DR-1: tekillik artık FATURA başına. AYNI faturaya aynı içerik (sha) iki kez → ihlal.
        String sha = "a".repeat(64);
        Invoice invoice = newInvoice("DupInvoice");

        FileAsset first = newFile("dup1.pdf", sha, invoice);
        fileAssetRepository.saveAndFlush(first);

        FileAsset second = newFile("dup2.pdf", sha, invoice);

        assertThatThrownBy(() -> fileAssetRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameSha256DifferentInvoices_isAllowed() {
        // E2-DR-1 kazanımı: byte-aynı içerik İKİ FARKLI faturaya birer satırla bağlanabilir.
        String sha = "b".repeat(64);
        Invoice invA = newInvoice("ShareInvoiceA");
        Invoice invB = newInvoice("ShareInvoiceB");

        fileAssetRepository.saveAndFlush(newFile("shared-a.pdf", sha, invA));
        // Aynı sha, FARKLI fatura → bileşik tekil ÇAKMAZ.
        fileAssetRepository.saveAndFlush(newFile("shared-b.pdf", sha, invB));

        assertThat(fileAssetRepository.count()).isEqualTo(2);
    }

    @Test
    void multipleNullSha256_areAllowed() {
        FileAsset a = newFile("null-a.pdf", null, null);
        FileAsset b = newFile("null-b.pdf", null, null);

        fileAssetRepository.saveAndFlush(a);
        // Both null sha256 → must NOT conflict (partial-unique / NULL-not-equal semantics).
        fileAssetRepository.saveAndFlush(b);

        assertThat(fileAssetRepository.count()).isEqualTo(2);
    }

    // --- helpers ------------------------------------------------------------

    private static Service newService(String name, Provider provider) {
        Service s = new Service();
        s.setName(name);
        s.setProvider(provider);
        s.setFrequency(Frequency.MONTHLY);
        s.setActiveState(ActiveState.YES);
        return s;
    }

    /** Distinct period üretmek için sayaç ((period_year, period_month) tekil + code tekil). */
    private int periodSeq = 0;

    /** Minimal persisted Invoice (Provider→Service→Period→Expense→Invoice zinciri). */
    private Invoice newInvoice(String key) {
        Provider provider = new Provider();
        provider.setName(key + " Provider");
        provider = providerRepository.save(provider);

        Service service = newService(key + " Service", provider);
        service = serviceRepository.save(service);

        int seq = ++periodSeq; // her invoice'a benzersiz (year, month) + code ver
        Period period = new Period();
        period.setYear(2000 + seq);
        period.setMonth(1);
        period.setCode("period-" + seq);
        period = periodRepository.save(period);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCurrency(Currency.USD);
        expense.setAmount(new BigDecimal("100.00"));
        expense = expenseRepository.save(expense);

        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(provider);
        invoice.setStatus(InvoiceStatus.EXPECTED);
        return invoiceRepository.save(invoice);
    }

    private static FileAsset newFile(String fileName, String sha256, Invoice invoice) {
        FileAsset f = new FileAsset();
        f.setInvoice(invoice);
        f.setFilePath("faturalar/2026-01/" + fileName);
        f.setFileName(fileName);
        f.setFileType(FileType.PDF);
        f.setMimeType("application/pdf");
        f.setSizeBytes(1234L);
        f.setSha256(sha256);
        return f;
    }
}
