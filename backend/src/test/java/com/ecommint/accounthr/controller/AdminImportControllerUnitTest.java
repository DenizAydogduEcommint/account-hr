package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ecommint.accounthr.config.ImportProperties;
import com.ecommint.accounthr.dto.importer.InvoiceFileImportSummary;
import com.ecommint.accounthr.service.importer.InvoiceFileImportException;
import com.ecommint.accounthr.service.importer.InvoiceFileImportService;

/**
 * E2-03 — {@link AdminImportController#importInvoiceFiles} güvenlik/yol-sınırı testi
 * (Spring context GEREKTİRMEZ). İstekteki {@code sourceDir} izinli kaynak kökünün
 * ALTINDA olmalı; keyfi dizinler (ör. /etc) reddedilir.
 */
class AdminImportControllerUnitTest {

    @TempDir
    Path allowedBase;

    /** Test stub: scanAndImport'u override eden minimal InvoiceFileImportService. */
    private static final class StubService extends InvoiceFileImportService {
        private final java.util.function.Function<Path, InvoiceFileImportSummary> fn;

        StubService(java.util.function.Function<Path, InvoiceFileImportSummary> fn) {
            super(null, null, null);
            this.fn = fn;
        }

        @Override
        public InvoiceFileImportSummary scanAndImport(Path sourceDir) {
            return fn.apply(sourceDir);
        }
    }

    private AdminImportController controller(InvoiceFileImportService svc) {
        ImportProperties props = new ImportProperties();
        props.setInvoiceFilesSourceDir(allowedBase.toString());
        return new AdminImportController(null, null, svc, props);
    }

    @Test
    void rejectsSourceDirOutsideAllowedRoot() {
        // Servis ASLA çağrılmamalı (reddedilmeden önce).
        StubService svc = new StubService(src -> {
            throw new AssertionError("scanAndImport çağrılmamalıydı: " + src);
        });
        AdminImportController controller = controller(svc);

        assertThatThrownBy(() -> controller.importInvoiceFiles("/etc"))
                .isInstanceOf(InvoiceFileImportException.class)
                .hasMessageContaining("izinli kaynak kökünün dışında");

        // Path traversal denemesi de kökten kaçamaz.
        assertThatThrownBy(() -> controller.importInvoiceFiles(allowedBase + "/../../escape"))
                .isInstanceOf(InvoiceFileImportException.class);
    }

    @Test
    void blankSourceDirUsesAllowedBase() {
        InvoiceFileImportSummary expected =
                new InvoiceFileImportSummary(0, 0, 0, 0, 0, 0, 0, java.util.List.of());
        StubService svc = new StubService(src -> {
            assertThat(src).isEqualTo(allowedBase.toAbsolutePath().normalize());
            return expected;
        });
        AdminImportController controller = controller(svc);

        assertThat(controller.importInvoiceFiles(null)).isSameAs(expected);
        assertThat(controller.importInvoiceFiles("  ")).isSameAs(expected);
    }

    @Test
    void rejectsWhenBaseNotConfigured() {
        ImportProperties props = new ImportProperties(); // invoiceFilesSourceDir = null
        StubService svc = new StubService(src -> {
            throw new AssertionError("çağrılmamalı");
        });
        AdminImportController controller = new AdminImportController(null, null, svc, props);

        assertThatThrownBy(() -> controller.importInvoiceFiles(null))
                .isInstanceOf(InvoiceFileImportException.class)
                .hasMessageContaining("tanımlı değil");
    }
}
