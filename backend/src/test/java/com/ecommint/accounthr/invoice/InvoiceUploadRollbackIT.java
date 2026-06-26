package com.ecommint.accounthr.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.service.InvoiceUploadService;

/**
 * FIX 1 — Transaction-synchronization tabanlı orphan-dosya temizliğinin doğrulaması.
 *
 * <p>{@code @Transactional} commit, {@code upload()} döndükten SONRA proxy sınırında olur;
 * bu yüzden commit-anı/rollback sonucu metot içi catch ile yakalanamaz. Dosyalar zaten
 * diske yazılmış olur. {@link InvoiceUploadService}, yazdığı yolları transaction'a bağlar
 * ve {@code afterCompletion(status != STATUS_COMMITTED)} ile bunları siler.
 *
 * <p>Bu IT, upload'u BAŞARIYLA çağıran ama SONRADAN aynı transaction içinde patlayan bir
 * dış {@code @Transactional} metot kullanır → rollback → afterCompletion fiziksel dosyayı
 * temizlemeli. (Metot içi catch tetiklenmez; sızıntı YALNIZCA sync ile kapanır.)
 */
@SpringBootTest
@ActiveProfiles("test")
class InvoiceUploadRollbackIT extends AbstractDataCleanupIT {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @TestConfiguration
    static class MutatorConfig {
        @Bean
        UploadRollbackMutator uploadRollbackMutator(InvoiceUploadService uploadService) {
            return new UploadRollbackMutator(uploadService);
        }
    }

    /** Upload'u çağırıp ardından AYNI transaction'da patlayan yardımcı bean. */
    @Component
    static class UploadRollbackMutator {
        private final InvoiceUploadService uploadService;

        UploadRollbackMutator(InvoiceUploadService uploadService) {
            this.uploadService = uploadService;
        }

        /** Upload başarılı; ardından RuntimeException → rollback (commit edilmez). */
        @Transactional
        public void uploadThenRollback(Long serviceId, String month, MultipartFile file) {
            uploadService.upload(serviceId, month, new BigDecimal("12.34"),
                    Currency.TRY, "rollback testi", false, List.of(file), null);
            throw new RuntimeException("forced rollback after upload");
        }
    }

    @Autowired private UploadRollbackMutator mutator;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private FileAssetRepository fileAssetRepository;

    private com.ecommint.accounthr.domain.Service seedService() {
        Provider provider = new Provider();
        provider.setName("Rollback Provider");
        providerRepository.save(provider);

        Card card = new Card();
        card.setBank("Akbank");
        card.setLastFour("3800");
        cardRepository.save(card);

        com.ecommint.accounthr.domain.Service s = new com.ecommint.accounthr.domain.Service();
        s.setName("Rollback Service");
        s.setProvider(provider);
        s.setDefaultCard(card);
        s.setFrequency(Frequency.MONTHLY);
        s.setActiveState(ActiveState.YES);
        s.setInformational(false);
        s.setApproxAmountTry(new BigDecimal("100.00"));
        return serviceRepository.save(s);
    }

    private static MultipartFile file(String name, byte[] content) {
        return new MultipartFile() {
            @Override public String getName() { return "files"; }
            @Override public String getOriginalFilename() { return name; }
            @Override public String getContentType() { return "application/pdf"; }
            @Override public boolean isEmpty() { return content.length == 0; }
            @Override public long getSize() { return content.length; }
            @Override public byte[] getBytes() { return content; }
            @Override public InputStream getInputStream() {
                return new java.io.ByteArrayInputStream(content);
            }
            @Override public void transferTo(java.io.File dest) throws IOException {
                Files.write(dest.toPath(), content);
            }
        };
    }

    /**
     * Upload yazılan dosyayı diske koyar; dış transaction rollback olunca afterCompletion
     * fiziksel dosyayı temizlemeli → diskte orphan KALMAMALI ve FileAsset DB'de olmamalı.
     */
    @Test
    void rollbackAfterUploadRemovesPhysicalFile() throws Exception {
        com.ecommint.accounthr.domain.Service s = seedService();
        byte[] content = "ORPHAN-CANDIDATE-CONTENT".getBytes();

        assertThatThrownBy(() -> mutator.uploadThenRollback(
                s.getId(), "2026-03", file("orphan_mart.pdf", content)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("forced rollback");

        // DB rollback: hiç FileAsset commit edilmedi.
        assertThat(fileAssetRepository.findAll()).isEmpty();

        // afterCompletion temizledi: 2026-03 altında orphan fiziksel dosya YOK.
        Path monthDir = storageRoot.resolve("2026-03");
        if (Files.isDirectory(monthDir)) {
            try (Stream<Path> stream = Files.list(monthDir)) {
                assertThat(stream.filter(Files::isRegularFile).count()).isZero();
            }
        }
    }
}
