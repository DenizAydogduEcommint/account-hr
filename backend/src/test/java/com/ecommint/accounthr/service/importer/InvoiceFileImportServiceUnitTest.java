package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ecommint.accounthr.domain.enums.FileType;

/**
 * E2-03 — Spring context'i GEREKTİRMEYEN saf birim testleri: dosya tipi tespiti,
 * taban ad türetme ve note-path normalizasyonu.
 */
class InvoiceFileImportServiceUnitTest {

    // --- detectFileType ---------------------------------------------------

    @Test
    void detectFileType_byExtensionAndName() {
        assertThat(InvoiceFileImportService.detectFileType("aws_mart.pdf")).isEqualTo(FileType.PDF);
        assertThat(InvoiceFileImportService.detectFileType("aws_mart.xml")).isEqualTo(FileType.XML);
        assertThat(InvoiceFileImportService.detectFileType("aws_mart_statement.pdf"))
                .isEqualTo(FileType.STATEMENT);
        assertThat(InvoiceFileImportService.detectFileType("claude_ai_refund_mart.pdf"))
                .isEqualTo(FileType.RECEIPT);
        assertThat(InvoiceFileImportService.detectFileType("some_receipt.pdf"))
                .isEqualTo(FileType.RECEIPT);
        assertThat(InvoiceFileImportService.detectFileType("weird.bin")).isEqualTo(FileType.OTHER);
        // XML uzantısı, isimde 'statement' geçse bile XML kazanır (uzantı önce).
        assertThat(InvoiceFileImportService.detectFileType("aws_statement.xml")).isEqualTo(FileType.XML);
    }

    // --- baseName ---------------------------------------------------------

    @Test
    void baseName_stripsExtensionAndKnownSuffixes() {
        assertThat(InvoiceFileImportService.baseName("aws_mart.pdf")).isEqualTo("aws_mart");
        assertThat(InvoiceFileImportService.baseName("aws_mart.xml")).isEqualTo("aws_mart");
        assertThat(InvoiceFileImportService.baseName("aws_mart_statement.pdf")).isEqualTo("aws_mart");
        assertThat(InvoiceFileImportService.baseName("aws_mart_receipt.pdf")).isEqualTo("aws_mart");
        // Bilinmeyen ek korunur (yanlış kardeşlik kurmamak için).
        assertThat(InvoiceFileImportService.baseName("claude_ai_mart_1.pdf"))
                .isEqualTo("claude_ai_mart_1");
    }

    // --- folderBaseKey ----------------------------------------------------

    @Test
    void folderBaseKey_groupsSiblingsUnderSameKey() {
        assertThat(InvoiceFileImportService.folderBaseKey("2026-03/aws_mart.pdf"))
                .isEqualTo("2026-03/aws_mart");
        assertThat(InvoiceFileImportService.folderBaseKey("2026-03/aws_mart_statement.pdf"))
                .isEqualTo("2026-03/aws_mart");
        assertThat(InvoiceFileImportService.folderBaseKey("2026-03/aws_mart.xml"))
                .isEqualTo("2026-03/aws_mart");
        // Kök seviyesindeki (klasörsüz) dosya → kardeşlik kurulmaz.
        assertThat(InvoiceFileImportService.folderBaseKey("loose.pdf")).isNull();
    }

    // --- normalizeNotePath ------------------------------------------------

    @Test
    void normalizeNotePath_stripsFaturalarPrefix() {
        assertThat(InvoiceFileImportService.normalizeNotePath("faturalar/2026-03/aws_mart.pdf"))
                .isEqualTo("2026-03/aws_mart.pdf");
        // Önek yoksa olduğu gibi.
        assertThat(InvoiceFileImportService.normalizeNotePath("2026-02/claude_ai_subat_1.pdf"))
                .isEqualTo("2026-02/claude_ai_subat_1.pdf");
        // Ters bölü düzleştirilir.
        assertThat(InvoiceFileImportService.normalizeNotePath("faturalar\\2026-03\\aws_mart.pdf"))
                .isEqualTo("2026-03/aws_mart.pdf");
    }

    @Test
    void normalizeNotePath_rejectsFreeTextDescriptions() {
        // Path değil, serbest açıklama → null (yanlış eşleşme önlenir).
        assertThat(InvoiceFileImportService.normalizeNotePath("Faturayı bekliyoruz")).isNull();
        assertThat(InvoiceFileImportService.normalizeNotePath("e-Fatura sistemi üzerinden")).isNull();
        assertThat(InvoiceFileImportService.normalizeNotePath("   ")).isNull();
        assertThat(InvoiceFileImportService.normalizeNotePath(null)).isNull();
    }
}
