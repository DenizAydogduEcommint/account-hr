package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * E2-05 — {@link ReconciliationService} saf yardımcılarının (Spring context GEREKTİRMEZ)
 * birim testi: ana {@code TOPLAM:} satırı tespiti (Multinet/Sigorta bilgi-toplamlarını
 * HARİÇ tutar) ve ay-adı → period-kodu eşlemesi.
 */
class ReconciliationServiceUnitTest {

    // ---------------------------------------------------------------------------
    // Ana TOPLAM etiketi tespiti (bilgi-amaçlı hariç)
    // ---------------------------------------------------------------------------

    @Test
    void mainTotalLabel_acceptsPlainTotal() {
        assertThat(ReconciliationService.isMainTotalLabel("TOPLAM:")).isTrue();
        assertThat(ReconciliationService.isMainTotalLabel("toplam:")).isTrue();
        assertThat(ReconciliationService.isMainTotalLabel("  TOPLAM:  ")).isTrue();
    }

    @Test
    void mainTotalLabel_rejectsInformationalTotals() {
        // Bilgi-amaçlı (Ignored) bölümlerin alt toplamları ana TOPLAM SAYILMAZ.
        assertThat(ReconciliationService.isMainTotalLabel("MULTİNET TOPLAM:")).isFalse();
        assertThat(ReconciliationService.isMainTotalLabel("MULTINET TOPLAM:")).isFalse();
        assertThat(ReconciliationService.isMainTotalLabel("SİGORTA TOPLAM:")).isFalse();
        assertThat(ReconciliationService.isMainTotalLabel("SIGORTA TOPLAM:")).isFalse();
        assertThat(ReconciliationService.isMainTotalLabel("Multinet Yemek Kartı TOPLAM:")).isFalse();
    }

    @Test
    void mainTotalLabel_rejectsNonTotal() {
        assertThat(ReconciliationService.isMainTotalLabel(null)).isFalse();
        assertThat(ReconciliationService.isMainTotalLabel("")).isFalse();
        assertThat(ReconciliationService.isMainTotalLabel("Claude AI")).isFalse();
        assertThat(ReconciliationService.isMainTotalLabel("ARA TOPLAM")).isFalse(); // ":" yok
    }

    // ---------------------------------------------------------------------------
    // rowTotalLabel — satır içinde TOPLAM etiketi bulma
    // ---------------------------------------------------------------------------

    @Test
    void rowTotalLabel_findsMainAndInformationalLabels() {
        DataFormatter df = new DataFormatter();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Mart");

            Row mainRow = sheet.createRow(0);
            mainRow.createCell(1).setCellValue("TOPLAM:"); // etiket ikinci kolonda
            mainRow.createCell(5).setCellValue(1000.0);
            String mainLabel = ReconciliationService.rowTotalLabel(mainRow, df);
            assertThat(mainLabel).isEqualTo("TOPLAM:");
            assertThat(ReconciliationService.isMainTotalLabel(mainLabel)).isTrue();

            Row infoRow = sheet.createRow(1);
            infoRow.createCell(0).setCellValue("MULTİNET TOPLAM:");
            String infoLabel = ReconciliationService.rowTotalLabel(infoRow, df);
            assertThat(infoLabel).isEqualTo("MULTİNET TOPLAM:");
            assertThat(ReconciliationService.isMainTotalLabel(infoLabel)).isFalse();

            Row dataRow = sheet.createRow(2);
            dataRow.createCell(1).setCellValue("Claude AI");
            assertThat(ReconciliationService.rowTotalLabel(dataRow, df)).isNull();

            assertThat(ReconciliationService.rowTotalLabel(null, df)).isNull();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------------------
    // Ay-adı → period-kodu
    // ---------------------------------------------------------------------------

    @Test
    void monthNameToPeriodCode_mapsKnownMonths() {
        assertThat(ReconciliationService.monthNameToPeriodCode("Ocak")).isEqualTo("2026-01");
        assertThat(ReconciliationService.monthNameToPeriodCode("Şubat")).isEqualTo("2026-02");
        assertThat(ReconciliationService.monthNameToPeriodCode("Mart")).isEqualTo("2026-03");
        assertThat(ReconciliationService.monthNameToPeriodCode("Nisan")).isEqualTo("2026-04");
        assertThat(ReconciliationService.monthNameToPeriodCode("  Mart  ")).isEqualTo("2026-03");
    }

    @Test
    void monthNameToPeriodCode_unknownReturnsNull() {
        assertThat(ReconciliationService.monthNameToPeriodCode("Servisler")).isNull();
        assertThat(ReconciliationService.monthNameToPeriodCode("Mayıs")).isNull();
        assertThat(ReconciliationService.monthNameToPeriodCode(null)).isNull();
        assertThat(ReconciliationService.monthNameToPeriodCode("")).isNull();
    }
}
