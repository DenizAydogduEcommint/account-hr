package com.ecommint.accounthr.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ecommint.accounthr.config.ImportProperties;
import com.ecommint.accounthr.dto.importer.InvoiceFileImportSummary;
import com.ecommint.accounthr.service.importer.InvoiceFileImportService;

/**
 * E2-03 — {@link GlobalExceptionHandler} {@link com.ecommint.accounthr.service.importer.InvoiceFileImportException}
 * eşlemesi: caller/validation hatası (sourceDir izinli kök DIŞINDA) HTTP 400
 * {@code IMPORT_ERROR} döndürmeli — genel handler'a düşüp 500 OLMAMALI.
 *
 * <p>Standalone MockMvc (Spring context / DB gerektirmez): gerçek controller +
 * gerçek {@code @RestControllerAdvice} ile uçtan uca HTTP yanıt şekli doğrulanır.
 */
class InvoiceFileImportExceptionHandlerTest {

    @TempDir
    Path allowedBase;

    /** scanAndImport'a hiç gelmemeli (sourceDir doğrulamada reddedilir). */
    private static final class StubService extends InvoiceFileImportService {
        StubService() {
            super(null, null, null);
        }

        @Override
        public InvoiceFileImportSummary scanAndImport(Path sourceDir) {
            throw new AssertionError("scanAndImport çağrılmamalıydı: " + sourceDir);
        }
    }

    private MockMvc mockMvc() {
        ImportProperties props = new ImportProperties();
        props.setInvoiceFilesSourceDir(allowedBase.toString());
        AdminImportController controller =
                new AdminImportController(null, null, new StubService(), props);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void badSourceDirOutsideBase_returns400ImportError_not500() throws Exception {
        mockMvc().perform(post("/api/v1/admin/imports/invoice-files")
                        .param("sourceDir", "/etc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("IMPORT_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/admin/imports/invoice-files"));
    }
}
