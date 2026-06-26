package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;

/**
 * E3-08 — Rol tabanlı yetkilendirme matrisi uçtan-uca kanıtı.
 *
 * <p>Üç rolü (ADMIN / ACCOUNTING / TEAM_MEMBER) tohumlar, her biri için JWT alır ve
 * matrisin temsilci uçlarını HTTP üzerinden doğrular. Backend, UI gizlemeden bağımsız
 * olarak tek otoritedir: yetkisiz çağrı 403 döner (500 değil — {@code GlobalExceptionHandler}
 * {@code AccessDeniedException}'ı Spring'in {@code RestAccessDeniedHandler}'ına yeniden
 * fırlatır).
 *
 * <p>Matris (uygulanan haliyle):
 * <pre>
 * Uç                                    ADMIN  ACCOUNTING  TEAM_MEMBER
 * GET  /expenses                          ✓        ✓           ✓
 * POST /invoices (fatura yükle)           ✓        ✓           ✓
 * PATCH /expenses/{id}/status             ✓        ✓           ✗ (403)
 * GET  /files/{id}/download               ✓        ✓           ✓
 * POST /services (servis oluştur)         ✓        ✗ (403)     ✗ (403)
 * POST /admin/imports/excel               ✓        ✗ (403)     ✗ (403)
 * </pre>
 *
 * <p>Storage kökü {@link TempDir} ile izole; {@link AbstractDataCleanupIT} her test
 * öncesi/sonrası truncate eder → her surefire sırasında geçer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoleAuthorizationIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private FileAssetRepository fileAssetRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private String accountingEmail;
    private String memberEmail;
    private Long fileId;

    @BeforeEach
    void seed() throws Exception {
        adminEmail = "role.admin@e-commint.com";
        accountingEmail = "role.accounting@e-commint.com";
        memberEmail = "role.member@e-commint.com";
        userRepository.save(user(adminEmail, UserRole.ADMIN));
        userRepository.save(user(accountingEmail, UserRole.ACCOUNTING));
        userRepository.save(user(memberEmail, UserRole.TEAM_MEMBER));

        // Fiziksel dosya + FileAsset — download çağrısı için (200 ispatı).
        Path dir = storageRoot.resolve("2026-03");
        Files.createDirectories(dir);
        Path file = dir.resolve("role_test.pdf");
        Files.write(file, "PDF-BYTES".getBytes());

        FileAsset asset = new FileAsset();
        asset.setFilePath("2026-03/role_test.pdf");
        asset.setFileName("role_test.pdf");
        asset.setFileType(FileType.PDF);
        asset.setMimeType("application/pdf");
        asset.setSizeBytes((long) "PDF-BYTES".length());
        asset.setSha256("cafebabe");
        fileId = fileAssetRepository.save(asset).getId();
    }

    private AppUser user(String email, UserRole role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName("Role Test " + role);
        u.setRole(role);
        u.setActive(true);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return u;
    }

    // ------------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders bearer(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        return headers;
    }

    private HttpStatusCode getExpenses(String email) {
        return rest.exchange("/api/v1/expenses?month=2026-03",
                HttpMethod.GET, new HttpEntity<>(bearer(email)), String.class)
                .getStatusCode();
    }

    private HttpStatusCode downloadFile(String email) {
        return rest.exchange("/api/v1/files/" + fileId + "/download",
                HttpMethod.GET, new HttpEntity<>(bearer(email)), byte[].class)
                .getStatusCode();
    }

    /** PATCH status: id mevcut değil → ADMIN/ACCOUNTING rol kapısını geçer (404/200, 403 değil). */
    private HttpStatusCode patchStatus(String email) {
        HttpHeaders headers = bearer(email);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/expenses/999999/status",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "FOUND"), headers), String.class)
                .getStatusCode();
    }

    /** POST invoices upload: serviceId yok → rol kapısını geçen roller için 403 DEĞİL. */
    private HttpStatusCode uploadInvoice(String email) {
        HttpHeaders headers = bearer(email);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ByteArrayResource fileRes = new ByteArrayResource("INV-BYTES".getBytes()) {
            @Override
            public String getFilename() {
                return "inv.pdf";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("serviceId", "999999");
        body.add("month", "2026-03");
        body.add("files", fileRes);
        return rest.postForEntity("/api/v1/invoices",
                new HttpEntity<>(body, headers), String.class).getStatusCode();
    }

    /** POST services (servis oluştur): yalnızca ADMIN. */
    private HttpStatusCode createService(String email) {
        HttpHeaders headers = bearer(email);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/services",
                new HttpEntity<>(Map.of("name", "Role Test Service"), headers), String.class)
                .getStatusCode();
    }

    /** POST admin/imports/excel: yalnızca ADMIN. */
    private HttpStatusCode adminImport(String email) {
        HttpHeaders headers = bearer(email);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ByteArrayResource fileRes = new ByteArrayResource("XLSX-BYTES".getBytes()) {
            @Override
            public String getFilename() {
                return "import.xlsx";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileRes);
        return rest.postForEntity("/api/v1/admin/imports/excel",
                new HttpEntity<>(body, headers), String.class).getStatusCode();
    }

    // ------------------------------------------------------------------------
    // TEAM_MEMBER
    // ------------------------------------------------------------------------

    @Test
    void teamMemberCanReadExpenses() {
        assertThat(getExpenses(memberEmail)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void teamMemberCanDownloadFile() {
        assertThat(downloadFile(memberEmail)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void teamMemberUploadInvoiceNotForbidden() {
        assertThat(uploadInvoice(memberEmail)).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teamMemberCannotPatchStatus() {
        assertThat(patchStatus(memberEmail)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teamMemberCannotCreateService() {
        assertThat(createService(memberEmail)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void teamMemberCannotAdminImport() {
        assertThat(adminImport(memberEmail)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------------
    // ACCOUNTING
    // ------------------------------------------------------------------------

    @Test
    void accountingCanReadExpenses() {
        assertThat(getExpenses(accountingEmail)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void accountingCanDownloadFile() {
        assertThat(downloadFile(accountingEmail)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void accountingPatchStatusNotForbidden() {
        assertThat(patchStatus(accountingEmail)).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accountingCannotCreateService() {
        assertThat(createService(accountingEmail)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accountingCannotAdminImport() {
        assertThat(adminImport(accountingEmail)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------------------
    // ADMIN — temsilci çağrıların hiçbiri 403 DEĞİL
    // ------------------------------------------------------------------------

    @Test
    void adminCanReadExpenses() {
        assertThat(getExpenses(adminEmail)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminCanDownloadFile() {
        assertThat(downloadFile(adminEmail)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminPatchStatusNotForbidden() {
        assertThat(patchStatus(adminEmail)).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminUploadInvoiceNotForbidden() {
        assertThat(uploadInvoice(adminEmail)).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCreateServiceNotForbidden() {
        assertThat(createService(adminEmail)).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminImportNotForbidden() {
        assertThat(adminImport(adminEmail)).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
