package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E2-04 — {@link StatusColors} birim testi (Spring context GEREKTİRMEZ).
 *
 * <p>Renk→durum eşlemesini, ARGB son-6-hane (RGB) çıkarımını ve FF9800
 * (Araştırılacak↔Ignored) belirsizliğini doğrular.
 */
class StatusColorsTest {

    @Test
    void hexToStatusMapping() {
        assertThat(StatusColors.fromHex("4CAF50")).isEqualTo(InvoiceStatus.FOUND);
        assertThat(StatusColors.fromHex("8BC34A")).isEqualTo(InvoiceStatus.E_INVOICE);
        assertThat(StatusColors.fromHex("FF4444")).isEqualTo(InvoiceStatus.EXPECTED);
        // FF9800 → kanonik TO_INVESTIGATE (Ignored ayrımı metinle yapılır).
        assertThat(StatusColors.fromHex("FF9800")).isEqualTo(InvoiceStatus.TO_INVESTIGATE);
    }

    @Test
    void argbLastSixExtraction() {
        // POI XSSFColor#getARGBHex() 8 hane döner; son 6 = RGB.
        assertThat(StatusColors.fromHex("004CAF50")).isEqualTo(InvoiceStatus.FOUND);
        assertThat(StatusColors.fromHex("FF4CAF50")).isEqualTo(InvoiceStatus.FOUND);
        assertThat(StatusColors.fromHex("FFFF4444")).isEqualTo(InvoiceStatus.EXPECTED);
        assertThat(StatusColors.normalizeToRgb("004CAF50")).isEqualTo("4CAF50");
        assertThat(StatusColors.normalizeToRgb("FF8BC34A")).isEqualTo("8BC34A");
    }

    @Test
    void normalizeHandlesCaseHashAndInvalid() {
        assertThat(StatusColors.normalizeToRgb("4caf50")).isEqualTo("4CAF50");
        assertThat(StatusColors.normalizeToRgb("#FF4444")).isEqualTo("FF4444");
        assertThat(StatusColors.normalizeToRgb("#FF8BC34A")).isEqualTo("8BC34A"); // # + ARGB
        assertThat(StatusColors.normalizeToRgb(null)).isNull();
        assertThat(StatusColors.normalizeToRgb("")).isNull();
        assertThat(StatusColors.normalizeToRgb("ZZZ")).isNull();
        assertThat(StatusColors.normalizeToRgb("12345")).isNull(); // 5 hane geçersiz
    }

    @Test
    void unknownColorIsNull() {
        assertThat(StatusColors.fromHex("123456")).isNull();
        assertThat(StatusColors.fromHex(null)).isNull();
    }

    @Test
    void ambiguousColorDetection() {
        assertThat(StatusColors.isAmbiguousColor("FF9800")).isTrue();
        assertThat(StatusColors.isAmbiguousColor("FFFF9800")).isTrue(); // ARGB
        assertThat(StatusColors.isAmbiguousColor("4CAF50")).isFalse();
        assertThat(StatusColors.isAmbiguousColor("FF4444")).isFalse();
        assertThat(StatusColors.isAmbiguousColor(null)).isFalse();
    }

    @Test
    void statusToHexTableMatchesClaudeMd() {
        assertThat(StatusColors.STATUS_TO_HEX.get(InvoiceStatus.FOUND)).isEqualTo("4CAF50");
        assertThat(StatusColors.STATUS_TO_HEX.get(InvoiceStatus.E_INVOICE)).isEqualTo("8BC34A");
        assertThat(StatusColors.STATUS_TO_HEX.get(InvoiceStatus.EXPECTED)).isEqualTo("FF4444");
        assertThat(StatusColors.STATUS_TO_HEX.get(InvoiceStatus.TO_INVESTIGATE)).isEqualTo("FF9800");
        assertThat(StatusColors.STATUS_TO_HEX.get(InvoiceStatus.IGNORED)).isEqualTo("FF9800");
    }
}
