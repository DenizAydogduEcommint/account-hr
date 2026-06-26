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

import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

import jakarta.persistence.EntityManager;

/**
 * E1-DR-1/DR-2/DR-5 teknik-borç kapanışının DB-düzeyi (H2, ddl-auto=create-drop)
 * doğrulaması. Flyway testlerde kapalı olduğundan şema entity'lerden üretilir; bu
 * yüzden @Version kolonları, services tekil kısıtı ve files.sha256 tekil kısıtı
 * burada test şemasında da etkindir.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class OptimisticLockingAndUniquenessJpaTest {

    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;
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

    // --- E1-DR-5: files.sha256 partial-unique in test (H2) schema ----------

    @Test
    void duplicateNonNullSha256_violatesUniqueConstraint() {
        String sha = "a".repeat(64);

        FileAsset first = newFile("dup1.pdf", sha);
        fileAssetRepository.saveAndFlush(first);

        FileAsset second = newFile("dup2.pdf", sha);

        assertThatThrownBy(() -> fileAssetRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleNullSha256_areAllowed() {
        FileAsset a = newFile("null-a.pdf", null);
        FileAsset b = newFile("null-b.pdf", null);

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

    private static FileAsset newFile(String fileName, String sha256) {
        FileAsset f = new FileAsset();
        f.setFilePath("faturalar/2026-01/" + fileName);
        f.setFileName(fileName);
        f.setFileType(FileType.PDF);
        f.setMimeType("application/pdf");
        f.setSizeBytes(1234L);
        f.setSha256(sha256);
        return f;
    }
}
