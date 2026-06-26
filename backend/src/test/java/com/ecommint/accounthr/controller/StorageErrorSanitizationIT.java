package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.dto.ErrorResponse;
import com.ecommint.accounthr.service.storage.DuplicateFileException;
import com.ecommint.accounthr.service.storage.StorageException;
import com.ecommint.accounthr.service.storage.StorageIOException;
import com.ecommint.accounthr.service.storage.StoragePathTraversalException;

/**
 * E1 review #2 + #12: depolama hatalarının HTTP yanıt gövdesi mutlak dosya yolu / SHA-256
 * SIZDIRMAZ (sabit mesaj döner; orijinal yalnızca loga gider); ve gerçek I/O arızası
 * ({@link StorageIOException}) 503 SERVICE_UNAVAILABLE'a eşlenir (girdi-doğrulama
 * {@link StorageException} ise 400'de kalır).
 *
 * <p>Gerçek {@link GlobalExceptionHandler} bean'i üzerinden handler'ları doğrudan çağırır;
 * böylece bir IO arızasını fiziksel olarak tetiklemeden (deterministik) eşlemeleri doğrular.
 */
@SpringBootTest
@ActiveProfiles("test")
class StorageErrorSanitizationIT extends AbstractDataCleanupIT {

    private static final String LEAKY_PATH =
            "/Users/felece/account-hr-data/faturalar/2026-03/google_workspace_mart.pdf";
    private static final String LEAKY_SHA =
            "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";

    @Autowired private GlobalExceptionHandler handler;

    private MockHttpServletRequest request() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/files");
        return req;
    }

    /** StorageException (girdi-doğrulama): 400 + gövde dosya yolu içermez. */
    @Test
    void storageExceptionBodyHidesPath() {
        StorageException ex = new StorageException("Dosya yazılamadı: " + LEAKY_PATH);
        ResponseEntity<ErrorResponse> resp = handler.handleStorage(ex, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error()).isEqualTo("STORAGE_ERROR");
        assertThat(resp.getBody().message()).doesNotContain(LEAKY_PATH).doesNotContain("faturalar");
    }

    /** StorageIOException (gerçek I/O arızası): 503 + gövde dosya yolu içermez. */
    @Test
    void storageIoExceptionMapsTo503AndHidesPath() {
        StorageIOException ex = new StorageIOException("Dosya taşınamadı: " + LEAKY_PATH);
        ResponseEntity<ErrorResponse> resp = handler.handleStorageIO(ex, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().error()).isEqualTo("STORAGE_IO_ERROR");
        assertThat(resp.getBody().message()).doesNotContain(LEAKY_PATH).doesNotContain("faturalar");
    }

    /** DuplicateFileException: 409 + gövde SHA-256 sızdırmaz. */
    @Test
    void duplicateFileBodyHidesSha() {
        DuplicateFileException ex =
                new DuplicateFileException("Aynı içeriğe (SHA-256=" + LEAKY_SHA + ") sahip dosya zaten mevcut.");
        ResponseEntity<ErrorResponse> resp = handler.handleDuplicateFile(ex, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().error()).isEqualTo("DUPLICATE_FILE");
        assertThat(resp.getBody().message()).doesNotContain(LEAKY_SHA).doesNotContain("SHA-256");
    }

    /** StoragePathTraversalException: 400 + gövde yol sızdırmaz. */
    @Test
    void pathTraversalBodyHidesPath() {
        StoragePathTraversalException ex =
                new StoragePathTraversalException("Yol storage kökünün dışına çıkıyor: " + LEAKY_PATH);
        ResponseEntity<ErrorResponse> resp = handler.handlePathTraversal(ex, request());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error()).isEqualTo("INVALID_PATH");
        assertThat(resp.getBody().message()).doesNotContain(LEAKY_PATH).doesNotContain("faturalar");
    }
}
