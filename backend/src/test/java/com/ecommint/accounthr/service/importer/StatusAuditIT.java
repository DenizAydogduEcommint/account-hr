package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Service;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.importer.StatusAuditSummary;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E2-04 — {@link StatusAuditService} davranış testi (H2 + in-test renkli .xlsx).
 *
 * <p>{@link AbstractDataCleanupIT}'i extend eder → test sırasından bağımsız geçer
 * (CI alphabetical). Gerçek {@code 2026_Harcamalar.xlsx}'e BAĞIMLI DEĞİLDİR.
 *
 * <p>A bölümü (Excel metin/renk): POI ile {@code Fatura Durumu} hücreleri HEM metin HEM
 * solid-fill renkle üretilir; tutarlı/çelişkili/belirsiz senaryolar denetlenir.
 * B bölümü (DB): dosyası olan EXPECTED invoice'ın tutarsız raporlandığı + autofix ile
 * FOUND'a çekildiği doğrulanır.
 */
@SpringBootTest
@ActiveProfiles("test")
class StatusAuditIT extends AbstractDataCleanupIT {

    @Autowired private StatusAuditService statusAuditService;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;

    /** Fatura Durumu kolonu (12 kolon düzeni; 0-indexed). */
    private static final int COL_STATUS = 10;

    // ---------------------------------------------------------------------------
    // A — Excel metin/renk
    // ---------------------------------------------------------------------------

    /**
     * Workbook "Mart" sheet'i:
     * <pre>
     * row1: text "Bulundu"        + renk 4CAF50  → tutarlı (mismatch yok)
     * row2: text "e-Fatura"       + renk 8BC34A  → tutarlı
     * row3: text "Bulundu"        + renk FF4444  → ÇELİŞKİ (text FOUND, color EXPECTED)
     * row4: text "Ignored"        + renk FF9800  → belirsiz renk, metinle çözülür (mismatch YOK)
     * row5: text "Araştırılacak"  + renk FF9800  → belirsiz renk, metinle çözülür (mismatch YOK)
     * </pre>
     */
    private byte[] buildColoredWorkbook() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Mart");

            Row header = sheet.createRow(0);
            String[] headers = {"Tarih", "Hizmet", "Sağlayıcı", "Tutar", "Para Birimi",
                    "TL Karşılığı", "Kart", "Kullanan Takım", "Amaç", "Muhasebe E-posta",
                    "Fatura Durumu", "Fatura Notu"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            statusRow(wb, sheet, 1, "Bulundu", "4CAF50");        // tutarlı
            statusRow(wb, sheet, 2, "e-Fatura", "8BC34A");       // tutarlı
            statusRow(wb, sheet, 3, "Bulundu", "FF4444");        // ÇELİŞKİ
            statusRow(wb, sheet, 4, "Ignored", "FF9800");        // belirsiz → metinle
            statusRow(wb, sheet, 5, "Araştırılacak", "FF9800");  // belirsiz → metinle

            // Servisler sheet'i (atlanmalı): renkli ama denetime girmemeli.
            XSSFSheet servisler = wb.createSheet("Servisler");
            servisler.createRow(0).createCell(0).setCellValue("Hizmet");
            statusRow(wb, servisler, 1, "Bulundu", "FF4444"); // çelişki olsa da SAYILMAMALI

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Belirtilen satırın Fatura Durumu hücresine metin + solid-fill RGB renk yazar. */
    private void statusRow(XSSFWorkbook wb, XSSFSheet sheet, int rowIdx, String text, String rgbHex) {
        XSSFRow row = (XSSFRow) (sheet.getRow(rowIdx) != null ? sheet.getRow(rowIdx) : sheet.createRow(rowIdx));
        // Diğer kolonları gerçekçi tutmak gerekmez; denetim yalnızca Fatura Durumu'na bakar.
        row.createCell(1).setCellValue("Servis-" + rowIdx);
        XSSFCell cell = row.createCell(COL_STATUS);
        cell.setCellValue(text);

        byte[] rgb = new byte[] {
                (byte) Integer.parseInt(rgbHex.substring(0, 2), 16),
                (byte) Integer.parseInt(rgbHex.substring(2, 4), 16),
                (byte) Integer.parseInt(rgbHex.substring(4, 6), 16) };
        XSSFColor color = new XSSFColor(rgb, null);
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cell.setCellStyle(style);
    }

    @Test
    void detectsTextColorMismatchAndRespectsAmbiguity() {
        StatusAuditSummary summary = statusAuditService.auditStatuses(
                new ByteArrayInputStream(buildColoredWorkbook()), false);

        // Tam olarak BİR çelişki: row3 (text Bulundu / renk FF4444).
        assertThat(summary.textColorMismatch()).isEqualTo(1);
        assertThat(summary.mismatches()).hasSize(1);
        StatusAuditSummary.TextColorMismatch m = summary.mismatches().get(0);
        assertThat(m.sheet()).isEqualTo("Mart");
        assertThat(m.row()).isEqualTo(4); // 0-indexed row3 → 1-tabanlı 4
        assertThat(m.text()).isEqualTo("Bulundu");
        assertThat(m.textStatus()).isEqualTo(InvoiceStatus.FOUND);
        assertThat(m.colorHex()).isEqualTo("FF4444");
        assertThat(m.colorStatus()).isEqualTo(InvoiceStatus.EXPECTED);

        // FF9800 (Ignored + Araştırılacak) belirsizliği çelişki sayılmadı.
        assertThat(summary.mismatches()).noneMatch(x -> x.text().equals("Ignored"));
        assertThat(summary.mismatches()).noneMatch(x -> x.text().equals("Araştırılacak"));
    }

    // ---------------------------------------------------------------------------
    // B — DB dosya/durum
    // ---------------------------------------------------------------------------

    @Test
    void reportsFileStatusInconsistency_reportOnly() {
        Invoice expectedWithFile = seedInvoiceWithFile(InvoiceStatus.EXPECTED, true);
        Invoice foundWithFile = seedInvoiceWithFile(InvoiceStatus.FOUND, true);
        Invoice expectedNoFile = seedInvoiceWithFile(InvoiceStatus.EXPECTED, false);

        StatusAuditSummary summary = statusAuditService.auditStatuses(
                new ByteArrayInputStream(buildColoredWorkbook()), false);

        // Yalnızca "EXPECTED + dosyalı" tutarsız sayılır.
        assertThat(summary.fileStatusInconsistent()).isEqualTo(1);
        assertThat(summary.fileStatusFixed()).isZero();
        assertThat(summary.fileStatusInconsistencies())
                .singleElement()
                .satisfies(inc -> {
                    assertThat(inc.invoiceId()).isEqualTo(expectedWithFile.getId());
                    assertThat(inc.fixed()).isFalse();
                    assertThat(inc.fileCount()).isEqualTo(1);
                });

        // report-only: durum DEĞİŞMEDİ.
        assertThat(invoiceRepository.findById(expectedWithFile.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.EXPECTED);
        assertThat(invoiceRepository.findById(foundWithFile.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.FOUND);
        assertThat(invoiceRepository.findById(expectedNoFile.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.EXPECTED);

        assertThat(summary.undefinedStatus()).isZero();
    }

    @Test
    void autofixSetsExpectedWithFileToFound() {
        Invoice expectedWithFile = seedInvoiceWithFile(InvoiceStatus.EXPECTED, true);

        StatusAuditSummary summary = statusAuditService.auditStatuses(
                new ByteArrayInputStream(buildColoredWorkbook()), true);

        assertThat(summary.fileStatusInconsistent()).isEqualTo(1);
        assertThat(summary.fileStatusFixed()).isEqualTo(1);
        assertThat(summary.fileStatusInconsistencies())
                .singleElement()
                .satisfies(inc -> {
                    assertThat(inc.fixed()).isTrue();
                    assertThat(inc.newStatus()).isEqualTo(InvoiceStatus.FOUND);
                });

        // autofix: durum FOUND'a çekildi (kalıcı).
        assertThat(invoiceRepository.findById(expectedWithFile.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.FOUND);
    }

    @Test
    void statusDistributionReflectsDb() {
        seedInvoiceWithFile(InvoiceStatus.FOUND, false);
        seedInvoiceWithFile(InvoiceStatus.FOUND, false);
        seedInvoiceWithFile(InvoiceStatus.EXPECTED, false);

        StatusAuditSummary summary = statusAuditService.auditStatuses(
                new ByteArrayInputStream(buildColoredWorkbook()), false);

        assertThat(summary.statusDistribution().get(InvoiceStatus.FOUND)).isEqualTo(2L);
        assertThat(summary.statusDistribution().get(InvoiceStatus.EXPECTED)).isEqualTo(1L);
        assertThat(summary.undefinedStatus()).isZero();
    }

    // ---------------------------------------------------------------------------
    // Seed yardımcıları
    // ---------------------------------------------------------------------------

    private Invoice seedInvoiceWithFile(InvoiceStatus status, boolean withFile) {
        Provider provider = new Provider();
        provider.setName("P-" + System.nanoTime());
        providerRepository.save(provider);

        Service service = new Service();
        service.setName("S-" + System.nanoTime());
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        serviceRepository.save(service);

        Period period = periodRepository.findByCode("2026-03").orElseGet(() -> {
            Period p = new Period();
            p.setYear(2026);
            p.setMonth(3);
            p.setCode("2026-03");
            return periodRepository.save(p);
        });

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCurrency(Currency.USD);
        expense.setAmount(new BigDecimal("10.00"));
        expenseRepository.save(expense);

        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(provider);
        invoice.setStatus(status);
        invoice = invoiceRepository.save(invoice);

        if (withFile) {
            FileAsset file = new FileAsset();
            file.setInvoice(invoice);
            file.setFilePath("2026-03/file-" + invoice.getId() + ".pdf");
            file.setFileName("file-" + invoice.getId() + ".pdf");
            file.setFileType(FileType.PDF);
            fileAssetRepository.save(file);
        }
        return invoice;
    }
}
