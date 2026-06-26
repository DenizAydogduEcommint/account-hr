package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E3-09 — Fatura dosyası detay/önizleme uçları için integration test.
 *
 * <p>İki yeni uç doğrulanır (HTTP + JWT + security + servis zinciri, {@link ExpenseListIT}
 * deseni):
 * <ul>
 *   <li>{@code GET /api/v1/expenses/{id}/files} — expense'e bağlı FileAsset metadata listesi
 *       (fileType, mimeType, sizeBytes, previewable). Bilinmeyen expense → 404; dosyasız
 *       expense → 200 + boş liste.</li>
 *   <li>{@code GET /api/v1/files/{id}/preview} — inline akış (Content-Disposition: inline) +
 *       depolanan mimeType Content-Type olarak. Bilinmeyen id → 404; token'sız → 401.</li>
 * </ul>
 *
 * <p>Storage kökü {@link TempDir} + {@link DynamicPropertySource} ile izole; gerçek
 * STORAGE_ROOT / expenses'a dokunulmaz. {@link AbstractDataCleanupIT} sıra-bağımsız izolasyon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExpenseFilePreviewIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final byte[] PDF_BYTES = "%PDF-1.4 fake-bytes".getBytes();

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private Long expenseWithFileId;
    private Long expenseNoFilesId;
    private Long pdfFileId;
    private Long xmlFileId;

    @BeforeEach
    void seed() throws Exception {
        adminEmail = "e309.admin@e-commint.com";
        AppUser admin = new AppUser();
        admin.setEmail(adminEmail);
        admin.setFullName("E309 Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        Provider provider = new Provider();
        provider.setName("Anthropic E309");
        providerRepository.save(provider);

        com.ecommint.accounthr.domain.Service service = new com.ecommint.accounthr.domain.Service();
        service.setName("Claude AI E309");
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service.setInformational(false);
        serviceRepository.save(service);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(8);
        period.setCode("2026-08");
        periodRepository.save(period);

        Expense withFile = new Expense();
        withFile.setService(service);
        withFile.setPeriod(period);
        withFile.setTransactionDate(LocalDate.of(2026, 8, 15));
        withFile.setAmount(new java.math.BigDecimal("100.00"));
        withFile.setCurrency(Currency.TRY);
        withFile.setAmountTry(new java.math.BigDecimal("100.00"));
        withFile.setInformational(false);
        expenseRepository.save(withFile);
        expenseWithFileId = withFile.getId();

        Expense noFiles = new Expense();
        noFiles.setService(service);
        noFiles.setPeriod(period);
        noFiles.setTransactionDate(LocalDate.of(2026, 8, 16));
        noFiles.setAmount(new java.math.BigDecimal("50.00"));
        noFiles.setCurrency(Currency.TRY);
        noFiles.setAmountTry(new java.math.BigDecimal("50.00"));
        noFiles.setInformational(false);
        expenseRepository.save(noFiles);
        expenseNoFilesId = noFiles.getId();

        Invoice invoice = new Invoice();
        invoice.setExpense(withFile);
        invoice.setProvider(provider);
        invoice.setStatus(InvoiceStatus.FOUND);
        invoiceRepository.save(invoice);

        // Fiziksel dosyalar + FileAsset kayıtları (preview diske + kayda ihtiyaç duyar).
        Path dir = storageRoot.resolve("2026-08");
        Files.createDirectories(dir);

        Path pdf = dir.resolve("claude_agustos.pdf");
        Files.write(pdf, PDF_BYTES);
        FileAsset pdfAsset = new FileAsset();
        pdfAsset.setInvoice(invoice);
        pdfAsset.setFilePath("2026-08/claude_agustos.pdf");
        pdfAsset.setFileName("claude_agustos.pdf");
        pdfAsset.setFileType(FileType.PDF);
        pdfAsset.setMimeType("application/pdf");
        pdfAsset.setSizeBytes((long) PDF_BYTES.length);
        pdfAsset.setSha256("aaa111");
        pdfFileId = fileAssetRepository.save(pdfAsset).getId();

        Path xml = dir.resolve("claude_agustos.xml");
        Files.write(xml, "<invoice/>".getBytes());
        FileAsset xmlAsset = new FileAsset();
        xmlAsset.setInvoice(invoice);
        xmlAsset.setFilePath("2026-08/claude_agustos.xml");
        xmlAsset.setFileName("claude_agustos.xml");
        xmlAsset.setFileType(FileType.XML);
        xmlAsset.setMimeType("application/xml");
        xmlAsset.setSizeBytes((long) "<invoice/>".length());
        xmlAsset.setSha256("bbb222");
        xmlFileId = fileAssetRepository.save(xmlAsset).getId();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(adminEmail));
        return headers;
    }

    // ------------------------------------------------------------------------
    // GET /api/v1/expenses/{id}/files
    // ------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void expenseWithFilesReturnsMetadataList() {
        ResponseEntity<List> resp = rest.exchange(
                "/api/v1/expenses/" + expenseWithFileId + "/files",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> body = resp.getBody();
        assertThat(body).hasSize(2);

        Map<String, Object> pdfRow = body.stream()
                .filter(r -> "PDF".equals(r.get("fileType")))
                .findFirst().orElseThrow();
        assertThat(pdfRow.get("fileName")).isEqualTo("claude_agustos.pdf");
        assertThat(pdfRow.get("mimeType")).isEqualTo("application/pdf");
        assertThat(((Number) pdfRow.get("sizeBytes")).longValue()).isEqualTo(PDF_BYTES.length);
        assertThat(pdfRow.get("previewable")).isEqualTo(true);
        assertThat(pdfRow.get("invoiceStatus")).isEqualTo("FOUND");
        assertThat(pdfRow.get("invoiceId")).isNotNull();
        // Fiziksel yol ASLA dışa verilmez.
        assertThat(pdfRow).doesNotContainKey("filePath");

        Map<String, Object> xmlRow = body.stream()
                .filter(r -> "XML".equals(r.get("fileType")))
                .findFirst().orElseThrow();
        assertThat(xmlRow.get("mimeType")).isEqualTo("application/xml");
        assertThat(xmlRow.get("previewable")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void expenseWithNoFilesReturnsEmptyList() {
        ResponseEntity<List> resp = rest.exchange(
                "/api/v1/expenses/" + expenseNoFilesId + "/files",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void unknownExpenseReturns404() {
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/expenses/99999999/files",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }

    @Test
    void expenseFilesUnauthenticatedReturns401() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(
                "/api/v1/expenses/" + expenseWithFileId + "/files", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------------
    // GET /api/v1/files/{id}/preview
    // ------------------------------------------------------------------------

    @Test
    void previewReturnsInlinePdfWithStoredMimeType() {
        ResponseEntity<byte[]> resp = rest.exchange(
                "/api/v1/files/" + pdfFileId + "/preview",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(PDF_BYTES);

        String disposition = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).isNotNull();
        assertThat(disposition).startsWith("inline");
        assertThat(disposition).contains("claude_agustos.pdf");

        assertThat(resp.getHeaders().getContentType()).isNotNull();
        assertThat(resp.getHeaders().getContentType().toString()).startsWith("application/pdf");
    }

    @Test
    void previewXmlReturnsInlineTextXml() {
        ResponseEntity<byte[]> resp = rest.exchange(
                "/api/v1/files/" + xmlFileId + "/preview",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String disposition = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("inline");
        assertThat(resp.getHeaders().getContentType().toString()).contains("xml");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void previewUnknownIdReturns404() {
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/files/99999999/preview",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }

    @Test
    void previewUnauthenticatedReturns401() {
        ResponseEntity<String> resp = rest.getForEntity(
                "/api/v1/files/" + pdfFileId + "/preview", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void previewMissingPhysicalFileReturns404() throws Exception {
        // FileAsset kaydı var ama fiziksel dosya diskten silinmiş → 404 (400/500 DEĞİL).
        Files.delete(storageRoot.resolve("2026-08/claude_agustos.pdf"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/files/" + pdfFileId + "/preview",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }

    // ------------------------------------------------------------------------
    // GET /api/v1/files/{id}/download
    // ------------------------------------------------------------------------

    @Test
    void downloadReturnsAttachmentPdfStream() {
        ResponseEntity<byte[]> resp = rest.exchange(
                "/api/v1/files/" + pdfFileId + "/download",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), byte[].class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(PDF_BYTES);

        String disposition = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).isNotNull();
        assertThat(disposition).startsWith("attachment");
        assertThat(disposition).contains("claude_agustos.pdf");

        assertThat(resp.getHeaders().getContentType()).isNotNull();
        assertThat(resp.getHeaders().getContentType().toString()).startsWith("application/pdf");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void downloadUnknownIdReturns404() {
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/files/99999999/download",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }

    @Test
    void downloadMissingPhysicalFileReturns404() throws Exception {
        // E1 review #1: FileAsset kaydı var ama fiziksel dosya diskten silinmiş.
        // Eskiden download loadAsResource'u sarmadığı için 400 STORAGE_ERROR dönerdi;
        // yeni sözleşme önizleme ile tutarlı → 404 NOT_FOUND.
        Files.delete(storageRoot.resolve("2026-08/claude_agustos.pdf"));

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/files/" + pdfFileId + "/download",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }
}
