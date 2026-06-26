package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.importer.ImportSummary;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

import jakarta.persistence.EntityManager;

/**
 * E2-01 — {@link ExcelImportService} davranış testi (H2).
 *
 * <p>POI ile in-test bir {@code .xlsx} (gerçek 12 kolon yapısını taklit eden bir "Mart"
 * sheet'i) üretir, import eder ve sayaçları + alan eşlemelerini doğrular. Gerçek
 * {@code 2026_Harcamalar.xlsx} dosyasına BAĞIMLI DEĞİLDİR.
 */
@DataJpaTest
@Import({ JpaAuditingConfig.class, ExcelImportService.class })
@ActiveProfiles("test")
class ExcelImportServiceTest {

    @Autowired private ExcelImportService importService;
    @Autowired private CardRepository cardRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void seedCards() {
        seedCard("Akbank", "3800");
        seedCard("YKB", "3909");
        seedCard("Ziraat", "9164");
    }

    private void seedCard(String bank, String last4) {
        Card c = new Card();
        c.setBank(bank);
        c.setLastFour(last4);
        cardRepository.save(c);
    }

    /**
     * Test workbook'u: "Mart" sheet'i.
     * <pre>
     * row0: header (12 kolon)
     * row1: TL ödeme, Tutar BOŞ, TL Karşılığı dolu, kart 3800        (operasyonel)
     * row2: USD ödeme, Tutar=20, TL Karşılığı=700, kart 3909          (operasyonel)
     * row3: negatif iade, Tutar=-200, "Claude AI (İade)", kart 3909   (operasyonel, refund)
     * row4: Tarih BOŞ (Bekleniyor), USD=10, kart BOŞ                  (operasyonel, card null)
     * row5: TOPLAM: satırı                                            (skip-total)
     * row6: "Multinet Yemek Kartı ... Detay" bölüm başlığı            (skip, sets informational)
     * row7: Multinet harcaması, Ignored                              (informational)
     * row8: MULTİNET TOPLAM: satırı                                   (skip-total)
     * </pre>
     */
    private byte[] buildWorkbook() {
        try (Workbook wb = new XSSFWorkbook()) {
            CreationHelper helper = wb.getCreationHelper();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd.mm.yyyy"));

            Sheet sheet = wb.createSheet("Mart");

            // row0 header
            Row header = sheet.createRow(0);
            String[] headers = {"Tarih", "Hizmet", "Sağlayıcı", "Tutar", "Para Birimi",
                    "TL Karşılığı", "Kart", "Kullanan Takım", "Amaç", "Muhasebe E-posta",
                    "Fatura Durumu", "Fatura Notu"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // row1: TL ödeme, Tutar boş, TL Karşılığı dolu, kart 3800
            Row r1 = sheet.createRow(1);
            setDate(r1, 0, LocalDate.of(2026, 3, 1), dateStyle);
            r1.createCell(1).setCellValue("Google Workspace");
            r1.createCell(2).setCellValue("Google");
            // Tutar (3) boş bırakılır
            r1.createCell(4).setCellValue("TL");
            r1.createCell(5).setCellValue(1500.50);
            r1.createCell(6).setCellValue("****3800");
            r1.createCell(8).setCellValue("E-posta servisi");
            r1.createCell(10).setCellValue("Bulundu");
            r1.createCell(11).setCellValue("faturalar/2026-03/google_mart.pdf");

            // row2: USD ödeme, Tutar=20, TL=700, kart 3909
            Row r2 = sheet.createRow(2);
            setDate(r2, 0, LocalDate.of(2026, 3, 5), dateStyle);
            r2.createCell(1).setCellValue("Claude AI");
            r2.createCell(2).setCellValue("Anthropic");
            r2.createCell(3).setCellValue(20.0);
            r2.createCell(4).setCellValue("USD");
            r2.createCell(5).setCellValue(700.0);
            r2.createCell(6).setCellValue("****3909");
            r2.createCell(10).setCellValue("e-Fatura");

            // row3: negatif iade
            Row r3 = sheet.createRow(3);
            setDate(r3, 0, LocalDate.of(2026, 3, 6), dateStyle);
            r3.createCell(1).setCellValue("Claude AI (İade)");
            r3.createCell(2).setCellValue("Anthropic");
            r3.createCell(3).setCellValue(-200.0);
            r3.createCell(4).setCellValue("TL");
            r3.createCell(5).setCellValue(-200.0);
            r3.createCell(6).setCellValue("****3909");
            r3.createCell(10).setCellValue("Bulundu");

            // row4: Tarih boş, USD=10, kart boş, Bekleniyor
            Row r4 = sheet.createRow(4);
            // Tarih (0) boş
            r4.createCell(1).setCellValue("AWS");
            r4.createCell(2).setCellValue("Amazon");
            r4.createCell(3).setCellValue(10.0);
            r4.createCell(4).setCellValue("USD");
            // TL Karşılığı (5) boş, Kart (6) boş
            r4.createCell(10).setCellValue("Bekleniyor");

            // row5: TOPLAM satırı (5. kolon = index 4)
            Row r5 = sheet.createRow(5);
            r5.createCell(4).setCellValue("TOPLAM:");
            r5.createCell(5).setCellValue(2010.50);

            // row6: bölüm başlığı (Multinet)
            Row r6 = sheet.createRow(6);
            r6.createCell(0).setCellValue("Multinet Yemek Kartı (Bilgi Amaçlı) Detay");

            // row7: Multinet harcaması, Ignored (informational)
            Row r7 = sheet.createRow(7);
            setDate(r7, 0, LocalDate.of(2026, 3, 10), dateStyle);
            r7.createCell(1).setCellValue("Multinet Yükleme");
            r7.createCell(2).setCellValue("Multinet");
            r7.createCell(3).setCellValue(5000.0);
            r7.createCell(4).setCellValue("TL");
            r7.createCell(5).setCellValue(5000.0);
            r7.createCell(6).setCellValue("****9164");
            r7.createCell(10).setCellValue("Ignored");

            // row8: MULTİNET TOPLAM satırı
            Row r8 = sheet.createRow(8);
            r8.createCell(4).setCellValue("MULTİNET TOPLAM:");
            r8.createCell(5).setCellValue(5000.0);

            // Servisler sheet'i (atlanmalı)
            Sheet servisler = wb.createSheet("Servisler");
            servisler.createRow(0).createCell(0).setCellValue("Hizmet");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setDate(Row row, int col, LocalDate date, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(java.sql.Date.valueOf(date));
        cell.setCellStyle(style);
    }

    @Test
    void importsRowsClassifiesAndMapsFields() {
        ImportSummary summary = importService.importMonthlySheets(
                new ByteArrayInputStream(buildWorkbook()));
        entityManager.flush();
        entityManager.clear();

        // Tek tanınan sheet: Mart (Servisler atlandı).
        assertThat(summary.getSheets()).hasSize(1);
        ImportSummary.SheetSummary mart = summary.getSheets().get(0);
        assertThat(mart.getSheetName()).isEqualTo("Mart");
        assertThat(mart.getPeriodCode()).isEqualTo("2026-03");

        // 5 veri satırı (4 operasyonel + 1 informational) import edildi.
        assertThat(mart.getImported()).isEqualTo(5);
        // 2 TOPLAM satırı atlandı.
        assertThat(mart.getSkippedTotal()).isEqualTo(2);
        // Bölüm başlığı boş/atlanan sayacına yazıldı (en az 1).
        assertThat(mart.getSkippedEmpty()).isGreaterThanOrEqualTo(1);
        assertThat(summary.getTotalImported()).isEqualTo(5);

        List<Expense> expenses = expenseRepository.findAll();
        assertThat(expenses).hasSize(5);

        // Period oluşturuldu/bağlandı.
        assertThat(periodRepository.findByCode("2026-03")).isPresent();

        // --- Google Workspace (TL, Tutar boş, TL=1500.50, kart 3800) ---
        Expense google = findByService(expenses, "Google Workspace");
        assertThat(google.getAmount()).isNull();
        assertThat(google.getCurrency()).isEqualTo(Currency.TRY);
        assertThat(google.getAmountTry()).isEqualByComparingTo(new BigDecimal("1500.50"));
        assertThat(google.getCard()).isNotNull();
        assertThat(google.getCard().getLastFour()).isEqualTo("3800");
        assertThat(google.getTransactionDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(google.isInformational()).isFalse();
        Invoice googleInv = invoiceRepository.findByExpenseId(google.getId()).get(0);
        assertThat(googleInv.getStatus()).isEqualTo(InvoiceStatus.FOUND);
        assertThat(googleInv.getNote()).isEqualTo("faturalar/2026-03/google_mart.pdf");
        assertThat(googleInv.isRefund()).isFalse();

        // --- Claude AI (USD=20, TL=700, kart 3909, e-Fatura) ---
        Expense claude = findByService(expenses, "Claude AI");
        assertThat(claude.getAmount()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(claude.getCurrency()).isEqualTo(Currency.USD);
        assertThat(claude.getAmountTry()).isEqualByComparingTo(new BigDecimal("700"));
        assertThat(claude.getCard().getLastFour()).isEqualTo("3909");
        assertThat(invoiceRepository.findByExpenseId(claude.getId()).get(0).getStatus())
                .isEqualTo(InvoiceStatus.E_INVOICE);

        // --- Claude AI (İade) negatif refund ---
        Expense refund = findByService(expenses, "Claude AI (İade)");
        assertThat(refund.getAmount()).isEqualByComparingTo(new BigDecimal("-200"));
        Invoice refundInv = invoiceRepository.findByExpenseId(refund.getId()).get(0);
        assertThat(refundInv.isRefund()).isTrue();

        // --- AWS (Tarih boş, kart boş, Bekleniyor) ---
        Expense aws = findByService(expenses, "AWS");
        assertThat(aws.getTransactionDate()).isNull();
        assertThat(aws.getCard()).isNull();
        assertThat(aws.getCurrency()).isEqualTo(Currency.USD);
        assertThat(invoiceRepository.findByExpenseId(aws.getId()).get(0).getStatus())
                .isEqualTo(InvoiceStatus.EXPECTED);

        // --- Multinet (informational) ---
        Expense multinet = findByService(expenses, "Multinet Yükleme");
        assertThat(multinet.isInformational()).isTrue();
        assertThat(multinet.getCard().getLastFour()).isEqualTo("9164");
        assertThat(invoiceRepository.findByExpenseId(multinet.getId()).get(0).getStatus())
                .isEqualTo(InvoiceStatus.IGNORED);
    }

    @Test
    void importIsIdempotent() {
        byte[] wb = buildWorkbook();

        ImportSummary first = importService.importMonthlySheets(new ByteArrayInputStream(wb));
        entityManager.flush();
        entityManager.clear();
        assertThat(first.getTotalImported()).isEqualTo(5);

        // Aynı workbook tekrar → 0 yeni, hepsi duplicate.
        ImportSummary second = importService.importMonthlySheets(new ByteArrayInputStream(wb));
        entityManager.flush();
        entityManager.clear();

        assertThat(second.getTotalImported()).isEqualTo(0);
        assertThat(second.getSheets().get(0).getSkippedDuplicate()).isEqualTo(5);

        // Toplam expense sayısı çiftlenmedi.
        assertThat(expenseRepository.findAll()).hasSize(5);
    }

    /**
     * FIX 1: re-import pozisyona STABİL. Veri satırlarının ÜSTÜNE boş bir satır eklenip
     * sheet yeniden export edilse bile (alttaki satırların fiziksel indeksi kayar)
     * değişmeyen satırlar YENİDEN duplicate import EDİLMEZ. Eski algoritmada hash
     * rowIndex içerdiği için her kaydırma yeni hash → duplicate üretirdi.
     */
    @Test
    void reimportWithInsertedBlankRowAboveDoesNotDuplicate() {
        // 1. import: 4 operasyonel satır (orijinal düzen).
        byte[] original = buildSimpleSheet(false);
        ImportSummary first = importService.importMonthlySheets(new ByteArrayInputStream(original));
        entityManager.flush();
        entityManager.clear();
        assertThat(first.getTotalImported()).isEqualTo(2);
        assertThat(expenseRepository.findAll()).hasSize(2);

        // 2. import: AYNI veri ama üstte boş satır eklenmiş (tüm veri satırları 1 aşağı kaydı).
        byte[] shifted = buildSimpleSheet(true);
        ImportSummary second = importService.importMonthlySheets(new ByteArrayInputStream(shifted));
        entityManager.flush();
        entityManager.clear();

        // Pozisyon değişti ama iş-anahtarı aynı → 0 yeni, hepsi duplicate. Toplam çiftlenmedi.
        assertThat(second.getTotalImported()).isEqualTo(0);
        assertThat(second.getSheets().get(0).getSkippedDuplicate()).isEqualTo(2);
        assertThat(expenseRepository.findAll()).hasSize(2);
    }

    /**
     * FIX 1: aynı sheet'te byte-AYNI iki veri satırı (aynı period|hizmet|currency|amount|
     * amountTry|tarih) → İKİSİ de import edilir (pass-içi kopya-sayacı son-eki farklı hash
     * üretir). Re-import yine 0 yeni (idempotent).
     */
    @Test
    void twoByteIdenticalDataRowsAreBothImportedThenIdempotent() {
        byte[] wb = buildDuplicateRowsSheet();

        ImportSummary first = importService.importMonthlySheets(new ByteArrayInputStream(wb));
        entityManager.flush();
        entityManager.clear();
        // İki özdeş satır → ikisi de import (sayaç son-eki sayesinde).
        assertThat(first.getTotalImported()).isEqualTo(2);
        assertThat(expenseRepository.findAll()).hasSize(2);

        // Re-import: 0 yeni (sayaç deterministik → aynı iki hash yeniden üretilir).
        ImportSummary second = importService.importMonthlySheets(new ByteArrayInputStream(wb));
        entityManager.flush();
        entityManager.clear();
        assertThat(second.getTotalImported()).isEqualTo(0);
        assertThat(second.getSheets().get(0).getSkippedDuplicate()).isEqualTo(2);
        assertThat(expenseRepository.findAll()).hasSize(2);
    }

    /**
     * FIX 8: Fatura Notu (serbest metin, COL_NOTE) içinde "TOPLAM:" geçen bir VERİ satırı
     * yanlışlıkla TOPLAM satırı sanılıp DÜŞMEMELİ. Hizmet kolonu dolu olduğundan satır
     * gerçek bir harcamadır → import edilir.
     */
    @Test
    void dataRowWithToplamInNoteIsImportedNotSkipped() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Mart");
            writeHeader(sheet);

            Row r1 = sheet.createRow(1);
            r1.createCell(1).setCellValue("Vercel");
            r1.createCell(2).setCellValue("Vercel Inc");
            r1.createCell(3).setCellValue(15.0);
            r1.createCell(4).setCellValue("USD");
            r1.createCell(5).setCellValue(500.0);
            r1.createCell(6).setCellValue("****3800");
            r1.createCell(10).setCellValue("Bulundu");
            // Notta "TOPLAM:" geçiyor — eski kodda satır TOTAL sanılıp düşerdi.
            r1.createCell(11).setCellValue("Aylık TOPLAM: faturası; bkz panel");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            ImportSummary summary = importService.importMonthlySheets(
                    new ByteArrayInputStream(bos.toByteArray()));
            entityManager.flush();
            entityManager.clear();

            assertThat(summary.getTotalImported()).isEqualTo(1);
            List<Expense> expenses = expenseRepository.findAll();
            assertThat(expenses).hasSize(1);
            assertThat(expenses.get(0).getService().getName()).isEqualTo("Vercel");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] headers = {"Tarih", "Hizmet", "Sağlayıcı", "Tutar", "Para Birimi",
                "TL Karşılığı", "Kart", "Kullanan Takım", "Amaç", "Muhasebe E-posta",
                "Fatura Durumu", "Fatura Notu"};
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }
    }

    /**
     * 2 operasyonel satır içeren basit bir "Mart" sheet'i. {@code shiftDown=true} ise
     * veri satırlarının ÜSTÜNE boş bir satır eklenir (fiziksel indeksleri kaydırır) —
     * iş-anahtarları değişmez.
     */
    private byte[] buildSimpleSheet(boolean shiftDown) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Mart");
            writeHeader(sheet);

            int base = shiftDown ? 2 : 1; // shiftDown → row1 boş bırakılır, veri row2'den

            Row a = sheet.createRow(base);
            a.createCell(1).setCellValue("Claude AI");
            a.createCell(2).setCellValue("Anthropic");
            a.createCell(3).setCellValue(20.0);
            a.createCell(4).setCellValue("USD");
            a.createCell(5).setCellValue(700.0);
            a.createCell(6).setCellValue("****3909");
            a.createCell(10).setCellValue("Bulundu");

            Row b = sheet.createRow(base + 1);
            b.createCell(1).setCellValue("AWS");
            b.createCell(2).setCellValue("Amazon");
            b.createCell(3).setCellValue(10.0);
            b.createCell(4).setCellValue("USD");
            b.createCell(5).setCellValue(350.0);
            b.createCell(6).setCellValue("****3800");
            b.createCell(10).setCellValue("Bekleniyor");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** İki byte-AYNI veri satırı içeren "Mart" sheet'i (aynı iş-anahtarı). */
    private byte[] buildDuplicateRowsSheet() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Mart");
            writeHeader(sheet);

            for (int r = 1; r <= 2; r++) {
                Row row = sheet.createRow(r);
                row.createCell(1).setCellValue("Twilio");
                row.createCell(2).setCellValue("Twilio Inc");
                row.createCell(3).setCellValue(9.99);
                row.createCell(4).setCellValue("USD");
                row.createCell(5).setCellValue(330.0);
                row.createCell(6).setCellValue("****3909");
                row.createCell(10).setCellValue("Bulundu");
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * E1 review #10: bilinmeyen para birimi (TL/TRY/USD/EUR/GBP dışında) ARTIK SESSİZ
     * DEĞİL — TRY'ye düşer AMA bir {@code log.warn} üretir (parse edilen değer değişmez,
     * dolayısıyla satır-hash'i etkilenmez ve import yine başarılı olur).
     */
    @Test
    void unknownCurrencyImportsAsTryAndLogsWarning() {
        ch.qos.logback.classic.Logger importLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ExcelImportService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        importLogger.addAppender(appender);
        try {
            ImportSummary summary = importService.importMonthlySheets(
                    new ByteArrayInputStream(buildUnknownCurrencySheet()));
            entityManager.flush();
            entityManager.clear();

            // Satır TRY fallback ile import edildi.
            assertThat(summary.getTotalImported()).isEqualTo(1);
            List<Expense> expenses = expenseRepository.findAll();
            assertThat(expenses).hasSize(1);
            assertThat(expenses.get(0).getCurrency()).isEqualTo(Currency.TRY);

            // Bilinmeyen para birimi için WARN logu üretildi (artık sessiz değil).
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                        assertThat(event.getFormattedMessage())
                                .contains("Bilinmeyen para birimi")
                                .contains("XYZ");
                    });
        } finally {
            importLogger.detachAppender(appender);
        }
    }

    /** "Mart" sheet'i: tek satır, geçersiz Para Birimi "XYZ". */
    private byte[] buildUnknownCurrencySheet() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Mart");
            writeHeader(sheet);

            Row r1 = sheet.createRow(1);
            r1.createCell(1).setCellValue("Mystery Service");
            r1.createCell(2).setCellValue("Mystery Provider");
            r1.createCell(3).setCellValue(42.0);
            r1.createCell(4).setCellValue("XYZ"); // bilinmeyen para birimi
            r1.createCell(5).setCellValue(1000.0);
            r1.createCell(6).setCellValue("****3800");
            r1.createCell(10).setCellValue("Bulundu");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Expense findByService(List<Expense> expenses, String serviceName) {
        return expenses.stream()
                .filter(e -> serviceName.equals(e.getService().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expense bulunamadı: " + serviceName));
    }
}
