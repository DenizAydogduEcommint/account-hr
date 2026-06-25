package com.ecommint.accounthr.controller;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.dto.importer.ReconciliationReport;
import com.ecommint.accounthr.service.importer.ExcelImportException;
import com.ecommint.accounthr.service.importer.ReconciliationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E2-05 — Migrasyon doğrulama / mutabakat raporu ucu (SALT-OKUNUR).
 *
 * <p>{@code POST /api/v1/admin/reconciliation} — yüklenen {@code 2026_Harcamalar.xlsx}'i
 * DB ile mutabakat eder: her ay-period için Excel ana {@code TOPLAM:} TL'si ↔ DB ana
 * harcama TL toplamı (IGNORED bilgi-amaçlı bölümler hariç), satır/dosya/durum sayıları ve
 * importer idempotency anahtarları. Hiçbir mutasyon yapmaz. Yalnızca ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@Tag(name = "Admin Reconciliation", description = "Migrasyon doğrulama / mutabakat — yalnızca ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminReconciliationController {

    private final ReconciliationService reconciliationService;

    public AdminReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** Excel'i yükle ve DB ile mutabakat raporu üret (read-only). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Migrasyon mutabakat raporu üret",
            description = "2026_Harcamalar.xlsx'i yükler; her ay-period için Excel ana TOPLAM (TL) "
                    + "↔ DB ana harcama TL toplamını (IGNORED Multinet/Sigorta bölümleri hariç) "
                    + "±0.01 toleransla karşılaştırır, satır sayısı + fiziksel/DB dosya sayısı + "
                    + "durum dağılımını denetler ve importer idempotency anahtarlarını dokümante "
                    + "eder. NULL/boş toplam (ör. Nisan kısmi ay) WARNING'dir, MISMATCH değil. "
                    + "SALT-OKUNUR (DB'ye yazmaz). Yalnızca ADMIN.")
    public ReconciliationReport reconcile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExcelImportException("Boş dosya yüklenemez.");
        }
        try (InputStream in = file.getInputStream()) {
            return reconciliationService.reconcile(in);
        } catch (IOException e) {
            throw new ExcelImportException("Yüklenen dosya okunamadı.", e);
        }
    }
}
