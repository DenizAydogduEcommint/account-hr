package com.ecommint.accounthr.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.service.storage.StorageService;
import com.ecommint.accounthr.service.storage.StoredFile;

/**
 * FIX 3 (orphan dosya): metadata persist başarısız olduğunda az önce yazılan fiziksel
 * dosyanın diskte ORPHAN bırakılmadığını (silindiğini) kanıtlar.
 *
 * <p>{@link StorageService} mock'lanır: {@code store} stub'ı gerçek bir temp dosya yazar
 * ve {@code deletePhysical} stub'ı bu dosyayı gerçekten siler. {@code save} fırlatınca,
 * {@link FileUploadService} fiziksel dosyayı silmeli (orphan yok) ve hatayı yeniden
 * fırlatmalı.
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @TempDir
    Path storageRoot;

    @Mock
    private StorageService storageService;

    @Mock
    private FileAssetRepository fileAssetRepository;

    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService(storageService, fileAssetRepository);
    }

    @Test
    void persistFailure_removesOrphanPhysicalFile() throws IOException {
        String relativePath = "2026-02/orphan_subat.pdf";
        Path physical = storageRoot.resolve(relativePath);
        Files.createDirectories(physical.getParent());

        // store(): gerçek fiziksel dosyayı yazar ve StoredFile döner.
        when(storageService.store(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Files.writeString(physical, "orphan-bytes");
                    return new StoredFile(relativePath, "orphan_subat.pdf", "deadbeef", 12L);
                });
        // deletePhysical(): gerçek dosyayı sil (best-effort sözleşmesini taklit et).
        when(storageService.deletePhysical(eq(relativePath)))
                .thenAnswer(inv -> Files.deleteIfExists(physical));
        // Metadata persist patlasın.
        when(fileAssetRepository.save(any(FileAsset.class)))
                .thenThrow(new RuntimeException("DB save failed (constraint)"));

        Invoice invoice = new Invoice();
        invoice.setId(42L);

        assertThatThrownBy(() -> fileUploadService.upload(
                invoice,
                LocalDate.of(2026, 2, 28),
                "Orphan Service",
                null,
                null,
                "orig.pdf",
                new ByteArrayInputStream("orphan-bytes".getBytes(StandardCharsets.UTF_8)),
                "application/pdf",
                FileType.PDF,
                null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB save failed");

        // Orphan cleanup çağrıldı ve fiziksel dosya gerçekten silindi.
        verify(storageService, times(1)).deletePhysical(relativePath);
        assertThat(Files.exists(physical)).as("orphan dosya silinmiş olmalı").isFalse();
    }
}
