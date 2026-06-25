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

import com.ecommint.accounthr.dto.importer.ImportSummary;
import com.ecommint.accounthr.service.importer.ExcelImportException;
import com.ecommint.accounthr.service.importer.ExcelImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Yönetici veri-aktarım uçları (E2-01).
 *
 * <p>{@code POST /api/v1/admin/imports/excel} — {@code 2026_Harcamalar.xlsx} ay
 * sheet'lerini {@code expenses} (+ taslak {@code invoices}) tablosuna aktarır. Yalnızca
 * ADMIN rolüne açıktır ve kimlik doğrulama gerektirir. Idempotent: aynı dosyayı iki kez
 * yüklemek satırları çiftlemez.
 */
@RestController
@RequestMapping("/api/v1/admin/imports")
@Tag(name = "Admin Import", description = "Excel ay-sheet veri aktarımı — yalnızca ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminImportController {

    private final ExcelImportService excelImportService;

    public AdminImportController(ExcelImportService excelImportService) {
        this.excelImportService = excelImportService;
    }

    /** Excel dosyasını yükle ve ay sheet'lerini import et. */
    @PostMapping(path = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Excel ay-sheet'lerini import et",
            description = "2026_Harcamalar.xlsx'i yükler; Ocak/Şubat/Mart/Nisan sheet'lerini "
                    + "expenses + taslak invoices olarak aktarır. Servisler sheet'i atlanır. "
                    + "Idempotent (source_row_hash). Yalnızca ADMIN.")
    public ImportSummary importExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExcelImportException("Boş dosya yüklenemez.");
        }
        try (InputStream in = file.getInputStream()) {
            return excelImportService.importMonthlySheets(in);
        } catch (IOException e) {
            throw new ExcelImportException("Yüklenen dosya okunamadı.", e);
        }
    }
}
