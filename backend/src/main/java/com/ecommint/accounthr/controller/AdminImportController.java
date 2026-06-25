package com.ecommint.accounthr.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.config.ImportProperties;
import com.ecommint.accounthr.dto.importer.ImportSummary;
import com.ecommint.accounthr.dto.importer.InvoiceFileImportSummary;
import com.ecommint.accounthr.dto.importer.ServiceImportSummary;
import com.ecommint.accounthr.dto.importer.StatusAuditSummary;
import com.ecommint.accounthr.service.importer.ExcelImportException;
import com.ecommint.accounthr.service.importer.ExcelImportService;
import com.ecommint.accounthr.service.importer.InvoiceFileImportException;
import com.ecommint.accounthr.service.importer.InvoiceFileImportService;
import com.ecommint.accounthr.service.importer.ServiceMasterImportService;
import com.ecommint.accounthr.service.importer.StatusAuditService;

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
    private final ServiceMasterImportService serviceMasterImportService;
    private final InvoiceFileImportService invoiceFileImportService;
    private final StatusAuditService statusAuditService;
    private final ImportProperties importProperties;

    public AdminImportController(ExcelImportService excelImportService,
                                 ServiceMasterImportService serviceMasterImportService,
                                 InvoiceFileImportService invoiceFileImportService,
                                 StatusAuditService statusAuditService,
                                 ImportProperties importProperties) {
        this.excelImportService = excelImportService;
        this.serviceMasterImportService = serviceMasterImportService;
        this.invoiceFileImportService = invoiceFileImportService;
        this.statusAuditService = statusAuditService;
        this.importProperties = importProperties;
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

    /** {@code Servisler} master sheet'ini yükle ve servisleri UPSERT et (E2-02). */
    @PostMapping(path = "/services", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Servisler master sheet'ini import et",
            description = "2026_Harcamalar.xlsx'in 'Servisler' sheet'ini okur; servisleri "
                    + "normalize isim eşleşmesiyle UPSERT eder (E2-01'in minimal kayıtlarını "
                    + "gerçek frequency/active_state/informational/approx_amount/notes/active_months "
                    + "ile zenginleştirir), eksik servisleri oluşturur ve fatura e-postalarını "
                    + "service_contacts'a ekler. Eşleşmeyen servisler raporlanır. Idempotent. "
                    + "Yalnızca ADMIN.")
    public ServiceImportSummary importServices(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ExcelImportException("Boş dosya yüklenemez.");
        }
        try (InputStream in = file.getInputStream()) {
            return serviceMasterImportService.importServicesSheet(in);
        } catch (IOException e) {
            throw new ExcelImportException("Yüklenen dosya okunamadı.", e);
        }
    }

    /**
     * {@code faturalar/} klasörünü tarayıp dosyaları storage kökü altına KOPYALAR ve
     * invoice/files eşlemesini kurar (E2-03 — klasör tarama migrasyonu; multipart upload
     * DEĞİL). Kaynak dizin (Drive aynası) yalnızca OKUNUR; oraya asla yazılmaz/silinmez.
     */
    @PostMapping(path = "/invoice-files")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "faturalar/ klasörünü tara, kopyala ve invoice/files eşle",
            description = "Kaynak klasörü (Drive aynası, varsayılan expenses/faturalar — READ-ONLY) "
                    + "recursive tarar; her fizik dosyayı storage kökü altına göreli yolunu KORUYARAK "
                    + "kopyalar (yeniden adlandırma/klasörleme YOK). Dosyaları invoice 'Fatura Notu' "
                    + "path eşleşmesi + türev kardeş kuralıyla expense'lere bağlar; trash/ unmatched. "
                    + "Idempotent (SHA-256): 2. çalıştırma 0 yeni satır ekler. Yalnızca ADMIN.")
    public InvoiceFileImportSummary importInvoiceFiles(
            @RequestParam(name = "sourceDir", required = false) String sourceDir) {
        String base = importProperties.getInvoiceFilesSourceDir();
        if (base == null || base.isBlank()) {
            throw new InvoiceFileImportException(
                    "app.import.invoice-files-source-dir (INVOICE_FILES_SOURCE_DIR) tanımlı değil.");
        }
        Path baseDir = Paths.get(base).toAbsolutePath().normalize();

        Path source;
        if (sourceDir == null || sourceDir.isBlank()) {
            source = baseDir;
        } else {
            // Güvenlik (least privilege): istekteki sourceDir izinli kök ALTINDA olmalı.
            // Keyfi dizinlerin (ör. /etc) taranmasını engeller.
            source = Paths.get(sourceDir).toAbsolutePath().normalize();
            if (!source.startsWith(baseDir)) {
                throw new InvoiceFileImportException(
                        "sourceDir izinli kaynak kökünün dışında: " + sourceDir);
            }
        }
        return invoiceFileImportService.scanAndImport(source);
    }

    /**
     * Fatura durumu/renk tutarlılık denetimi (E2-04). Yüklenen Excel'de her ay-sheet
     * satırının {@code Fatura Durumu} METNİ ile DOLGU RENGİNİ çapraz doğrular (metin
     * önceliklidir) ve E2-03'te dosyası bulunduğu hâlde "Bekleniyor" kalan invoice'ları
     * tutarsızlık olarak raporlar. Varsayılan: yalnızca rapor ({@code autofix=false}).
     */
    @PostMapping(path = "/status-audit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Fatura durumu/renk tutarlılık denetimi",
            description = "2026_Harcamalar.xlsx'i yükler; ay-sheet'lerde Fatura Durumu metin↔renk "
                    + "tutarlılığını denetler (metin otoriter; FF9800 Araştırılacak↔Ignored "
                    + "belirsizliği metinle çözülür) ve dosyası bulunduğu hâlde EXPECTED kalan "
                    + "invoice'ları raporlar. autofix=true ise bu invoice'lar FOUND'a çekilir "
                    + "(varsayılan false → yalnızca rapor, manuel onay). Yalnızca ADMIN.")
    public StatusAuditSummary statusAudit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "autofix", defaultValue = "false") boolean autofix) {
        if (file == null || file.isEmpty()) {
            throw new ExcelImportException("Boş dosya yüklenemez.");
        }
        try (InputStream in = file.getInputStream()) {
            return statusAuditService.auditStatuses(in, autofix);
        } catch (IOException e) {
            throw new ExcelImportException("Yüklenen dosya okunamadı.", e);
        }
    }
}
