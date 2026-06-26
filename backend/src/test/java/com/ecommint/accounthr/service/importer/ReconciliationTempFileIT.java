package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.dto.importer.ReconciliationReport;

/**
 * FIX 6 — {@link ReconciliationService#reconcile} fiziksel dosya sayımı, yarım kalmış
 * atomik-yazma artıklarını ({@code .upload-*.tmp} / {@code .copy-*.tmp}) HARİÇ tutmalı.
 * Aksi halde fiziksel sayı şişer ve tutarsızlık notu yanlışlıkla SHA-256 dedup'ını suçlar.
 *
 * <p>{@link AbstractDataCleanupIT}'i extend eder → test sırasından bağımsız. Storage kökü
 * izole bir {@code @TempDir}'e yönlendirilir (gerçek STORAGE_ROOT değil); DB'de hiç
 * {@code files} satırı yoktur, bu yüzden geçici dosyalar sayılırsa fiziksel(>0) > DB(0)
 * olur ve dedup notu üretilir — düzeltme sonrası bu not OLMAMALI.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationTempFileIT extends AbstractDataCleanupIT {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired private ReconciliationService reconciliationService;

    @BeforeEach
    void cleanStorage() throws IOException {
        if (Files.isDirectory(storageRoot)) {
            try (var children = Files.list(storageRoot)) {
                for (Path child : children.toList()) {
                    Files.deleteIfExists(child);
                }
            }
        }
    }

    /** TOPLAM/satır mutabakatını ATLAYAN minimal workbook (tanınan ay sheet'i yok). */
    private byte[] emptyRecognizedSheetsWorkbook() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // Yalnızca tanınmayan bir sheet → period karşılaştırması tetiklenmez; sadece
            // dosya mutabakatı (countPhysicalFiles) yolu test edilir.
            XSSFSheet sheet = wb.createSheet("Servisler");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Hizmet");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void tempFilesAreNotCountedAsPhysicalFiles() throws IOException {
        // Yalnızca yarım kalmış atomik-yazma artıkları (gerçek kalıcı dosya YOK).
        write(storageRoot.resolve(".upload-abc123.tmp"), "partial");
        write(storageRoot.resolve(".copy-xyz789.tmp"), "partial");

        ReconciliationReport report = reconciliationService.reconcile(
                new ByteArrayInputStream(emptyRecognizedSheetsWorkbook()));

        // DB'de 0 files satırı. Temp dosyalar sayılMAZsa fiziksel=0 → tutarsızlık YOK.
        assertThat(report.filesPhysical()).isEqualTo(0L);
        assertThat(report.filesDbRows()).isEqualTo(0L);
        // SHA-256 dedup'ını suçlayan ("fiziksel > DB") not ÜRETİLMEMELİ.
        assertThat(report.inconsistencies())
                .noneMatch(s -> s.contains("fiziksel") && s.contains("dedup"));
    }

    @Test
    void realFilesAreStillCounted() throws IOException {
        // Gerçek bir kalıcı dosya + bir temp artık. Yalnızca gerçek dosya sayılmalı (1).
        write(storageRoot.resolve("real_invoice.pdf"), "real");
        write(storageRoot.resolve(".upload-abc.tmp"), "partial");

        ReconciliationReport report = reconciliationService.reconcile(
                new ByteArrayInputStream(emptyRecognizedSheetsWorkbook()));

        assertThat(report.filesPhysical()).isEqualTo(1L);
    }

    private void write(Path p, String content) throws IOException {
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }
}
