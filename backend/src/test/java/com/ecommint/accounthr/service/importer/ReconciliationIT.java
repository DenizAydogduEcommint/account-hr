package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Service;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.importer.ReconciliationReport;
import com.ecommint.accounthr.dto.importer.ReconciliationReport.PeriodReconciliation;
import com.ecommint.accounthr.dto.importer.ReconciliationReport.PeriodStatus;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E2-05 — {@link ReconciliationService} davranış testi (H2 + in-test .xlsx).
 *
 * <p>{@link AbstractDataCleanupIT}'i extend eder → test sırasından bağımsız geçer
 * (CI alphabetical). Gerçek {@code 2026_Harcamalar.xlsx}'e BAĞIMLI DEĞİLDİR; POI ile
 * "Mart" sheet'i (birkaç harcama satırı + ana {@code TOPLAM:} (col 6) + bir
 * {@code MULTİNET TOPLAM:} bilgi satırı) üretilir ve eşleşen DB kayıtları (biri IGNORED)
 * 2026-03 period'una seed edilir.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationIT extends AbstractDataCleanupIT {

    @Autowired private ReconciliationService reconciliationService;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    // 12 kolon düzeni (0-indexed).
    private static final int COL_DATE = 0;
    private static final int COL_SERVICE = 1;
    private static final int COL_AMOUNT_TRY = 5;

    // ---------------------------------------------------------------------------
    // Workbook üreticileri
    // ---------------------------------------------------------------------------

    /**
     * "Mart" sheet'i:
     * <pre>
     * row1: harcama  Claude AI   amountTry=100.00
     * row2: harcama  AWS         amountTry=250.50
     * row3: TOPLAM:              col6 = mainTotal  (ana)
     * row4: Multinet Yemek Kartı (bölüm başlığı)
     * row5: harcama  Multinet    amountTry=500.00  (bilgi-amaçlı → ana sayıma girmez)
     * row6: MULTİNET TOPLAM:     col6 = 500.00     (bilgi-toplamı → ana TOPLAM SAYILMAZ)
     * </pre>
     * Ana TOPLAM değeri parametreyle verilir (NULL → boş hücre = WARNING senaryosu).
     */
    private byte[] buildMartWorkbook(BigDecimal mainTotal) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Mart");

            Row header = sheet.createRow(0);
            String[] headers = {"Tarih", "Hizmet", "Sağlayıcı", "Tutar", "Para Birimi",
                    "TL Karşılığı", "Kart", "Kullanan Takım", "Amaç", "Muhasebe E-posta",
                    "Fatura Durumu", "Fatura Notu"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            expenseRow(sheet, 1, "Claude AI", 100.00);
            expenseRow(sheet, 2, "AWS", 250.50);

            Row totalRow = sheet.createRow(3);
            totalRow.createCell(COL_SERVICE).setCellValue("TOPLAM:");
            if (mainTotal != null) {
                totalRow.createCell(COL_AMOUNT_TRY).setCellValue(mainTotal.doubleValue());
            }

            // Bilgi-amaçlı bölüm: başlık + bir satır + bilgi-toplamı.
            Row sectionHeader = sheet.createRow(4);
            sectionHeader.createCell(COL_DATE).setCellValue("Multinet Yemek Kartı");
            expenseRow(sheet, 5, "Multinet Yükleme", 500.00);
            Row infoTotal = sheet.createRow(6);
            infoTotal.createCell(COL_DATE).setCellValue("MULTİNET TOPLAM:");
            infoTotal.createCell(COL_AMOUNT_TRY).setCellValue(500.00);

            // Servisler sheet'i (atlanmalı).
            wb.createSheet("Servisler").createRow(0).createCell(0).setCellValue("Hizmet");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void expenseRow(XSSFSheet sheet, int rowIdx, String service, double amountTry) {
        Row row = sheet.getRow(rowIdx) != null ? sheet.getRow(rowIdx) : sheet.createRow(rowIdx);
        row.createCell(COL_DATE).setCellValue("0" + rowIdx + ".03.2026");
        row.createCell(COL_SERVICE).setCellValue(service);
        row.createCell(COL_AMOUNT_TRY).setCellValue(amountTry);
    }

    // ---------------------------------------------------------------------------
    // DB seed
    // ---------------------------------------------------------------------------

    /** 2026-03'e: 2 ana harcama (100.00 + 250.50) + 1 IGNORED bilgi-amaçlı (500.00). */
    private void seedMarch() {
        Period march = period("2026-03", 2026, 3);
        seedExpenseWithInvoice(march, new BigDecimal("100.00"), InvoiceStatus.FOUND);
        seedExpenseWithInvoice(march, new BigDecimal("250.50"), InvoiceStatus.EXPECTED);
        // Bilgi-amaçlı (Multinet) → informational=true + IGNORED → ana toplama girmez.
        seedExpenseWithInvoice(march, new BigDecimal("500.00"), InvoiceStatus.IGNORED);
    }

    private Period period(String code, int year, int month) {
        return periodRepository.findByCode(code).orElseGet(() -> {
            Period p = new Period();
            p.setCode(code);
            p.setYear(year);
            p.setMonth(month);
            return periodRepository.save(p);
        });
    }

    private Expense seedExpenseWithInvoice(Period period, BigDecimal amountTry, InvoiceStatus status) {
        Provider provider = new Provider();
        provider.setName("P-" + System.nanoTime());
        providerRepository.save(provider);

        Service service = new Service();
        service.setName("S-" + System.nanoTime());
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        serviceRepository.save(service);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCurrency(Currency.TRY);
        expense.setAmountTry(amountTry);
        // E2-01 ile tutarlı: bilgi-amaçlı (Multinet/Sigorta) satırlar IGNORED + informational.
        expense.setInformational(status == InvoiceStatus.IGNORED);
        expenseRepository.save(expense);

        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(provider);
        invoice.setStatus(status);
        invoiceRepository.save(invoice);
        return expense;
    }

    private PeriodReconciliation march(ReconciliationReport report) {
        return report.periods().stream()
                .filter(p -> p.periodCode().equals("2026-03"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("2026-03 period raporda yok"));
    }

    // ---------------------------------------------------------------------------
    // Testler
    // ---------------------------------------------------------------------------

    @Test
    void reconcilesMainTotalExcludingIgnoredAndInformational() {
        seedMarch();
        // Excel ana TOPLAM = 350.50 (= 100.00 + 250.50); Multinet 500.00 ana toplama girmez.
        ReconciliationReport report = reconciliationService.reconcile(
                new ByteArrayInputStream(buildMartWorkbook(new BigDecimal("350.50"))));

        PeriodReconciliation march = march(report);
        assertThat(march.status()).isEqualTo(PeriodStatus.MATCH);
        assertThat(march.excelTotal()).isEqualByComparingTo("350.50");
        assertThat(march.dbTotal()).isEqualByComparingTo("350.50"); // IGNORED 500 hariç
        assertThat(march.diff().abs()).isLessThanOrEqualTo(new BigDecimal("0.01"));
        // Ana harcama satır sayısı: Excel 2 (Multinet bilgi satırı hariç) ↔ DB 2 (IGNORED hariç).
        assertThat(march.excelRowCount()).isEqualTo(2);
        assertThat(march.dbRowCount()).isEqualTo(2L);
        assertThat(report.ok()).isTrue();

        // Durum dağılımı: FOUND=1, EXPECTED=1, IGNORED=1.
        assertThat(report.statusDistribution().get(InvoiceStatus.FOUND)).isEqualTo(1L);
        assertThat(report.statusDistribution().get(InvoiceStatus.EXPECTED)).isEqualTo(1L);
        assertThat(report.statusDistribution().get(InvoiceStatus.IGNORED)).isEqualTo(1L);

        // Idempotency anahtarları dokümante edildi.
        assertThat(report.idempotencyKeys()).containsKeys(
                "expense (E2-01)", "service (E2-02)", "file (E2-03)");
    }

    @Test
    void offByAmountProducesMismatchAndInconsistency() {
        seedMarch();
        // Excel ana TOPLAM 360.50, DB 350.50 → fark 10.00 > tolerans → MISMATCH.
        ReconciliationReport report = reconciliationService.reconcile(
                new ByteArrayInputStream(buildMartWorkbook(new BigDecimal("360.50"))));

        PeriodReconciliation march = march(report);
        assertThat(march.status()).isEqualTo(PeriodStatus.MISMATCH);
        assertThat(march.diff()).isEqualByComparingTo("10.00");
        assertThat(report.ok()).isFalse();
        assertThat(report.inconsistencies())
                .anyMatch(s -> s.contains("2026-03") && s.contains("TUTAR"));
    }

    @Test
    void nullExcelTotalYieldsWarningNotMismatch() {
        seedMarch();
        // Ana TOPLAM hücresi boş → Excel toplamı NULL → WARNING (sert hata değil).
        ReconciliationReport report = reconciliationService.reconcile(
                new ByteArrayInputStream(buildMartWorkbook(null)));

        PeriodReconciliation march = march(report);
        assertThat(march.status()).isEqualTo(PeriodStatus.WARNING);
        assertThat(march.excelTotal()).isNull();
        // WARNING MISMATCH sayılmaz → genel ok TRUE.
        assertThat(report.ok()).isTrue();
        assertThat(report.inconsistencies())
                .anyMatch(s -> s.contains("2026-03") && s.contains("WARNING"));
    }

    @Test
    void rowCountMismatchProducesMismatch() {
        // Excel 2 ana satır ama DB'de yalnız 1 ANA harcama → satır uyumsuz.
        // Ayrıca bir bilgi-amaçlı (informational + IGNORED) satır seed edilir: count
        // sorgusu bilgi-satırını YANLIŞLIKLA sayarsa DB=2 olur (Excel 2 = DB 2 → MATCH);
        // doğru sorgu DB=1 verir → MISMATCH. Böylece test, count yolunun bilgi-satırını
        // HARİÇ tuttuğunu da aktif olarak korur (Issue 4 regresyon koruması).
        Period march = period("2026-03", 2026, 3);
        seedExpenseWithInvoice(march, new BigDecimal("350.50"), InvoiceStatus.FOUND);
        seedExpenseWithInvoice(march, new BigDecimal("500.00"), InvoiceStatus.IGNORED); // informational

        ReconciliationReport report = reconciliationService.reconcile(
                new ByteArrayInputStream(buildMartWorkbook(new BigDecimal("350.50"))));

        PeriodReconciliation m = march(report);
        // Tutar tutuyor (350.50 = 350.50; IGNORED 500 hariç) ama satır tutmuyor (Excel 2 ↔ DB 1).
        assertThat(m.excelTotal()).isEqualByComparingTo("350.50");
        assertThat(m.dbTotal()).isEqualByComparingTo("350.50");
        assertThat(m.excelRowCount()).isEqualTo(2);
        assertThat(m.dbRowCount()).isEqualTo(1L);
        assertThat(m.status()).isEqualTo(PeriodStatus.MISMATCH);
        assertThat(report.ok()).isFalse();
        assertThat(report.inconsistencies())
                .anyMatch(s -> s.contains("2026-03") && s.contains("SATIR"));
    }
}
