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
 * E1-04 — {@code /api/v1/files} okuma/taşıma uçları için geçici rol-kapısı testi.
 *
 * <p>Fatura PDF'leri hassas olduğundan download/list/trash/waiting yalnızca
 * ADMIN/ACCOUNTING rollerine açıktır (tam per-invoice sahiplik E3-08'e ertelendi).
 * Bu IT, kimliği doğrulanmış ama yetkisiz ({@code TEAM_MEMBER}) bir kullanıcının
 * 403 aldığını; ADMIN'in ise indirebildiğini doğrular.
 *
 * <p>Storage kökü {@link TempDir} + {@link DynamicPropertySource} ile izole edilir;
 * gerçek STORAGE_ROOT / expenses'a asla dokunulmaz. {@link AbstractDataCleanupIT}
 * before/after truncate ile sıra-bağımsız izolasyon sağlar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FileControllerAuthIT extends AbstractDataCleanupIT {

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
    private String memberEmail;
    private Long fileId;

    @BeforeEach
    void seed() throws Exception {
        adminEmail = "files.admin@e-commint.com";
        userRepository.save(user(adminEmail, UserRole.ADMIN));
        memberEmail = "files.member@e-commint.com";
        userRepository.save(user(memberEmail, UserRole.TEAM_MEMBER));

        // Fiziksel dosya + FileAsset (download çağrısı kayda + diske ihtiyaç duyar).
        Path dir = storageRoot.resolve("2026-03");
        Files.createDirectories(dir);
        Path file = dir.resolve("auth_test.pdf");
        Files.write(file, "PDF-BYTES".getBytes());

        FileAsset asset = new FileAsset();
        asset.setFilePath("2026-03/auth_test.pdf");
        asset.setFileName("auth_test.pdf");
        asset.setFileType(FileType.PDF);
        asset.setMimeType("application/pdf");
        asset.setSizeBytes((long) "PDF-BYTES".length());
        asset.setSha256("deadbeef");
        fileId = fileAssetRepository.save(asset).getId();
    }

    private AppUser user(String email, UserRole role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName("File Test " + role);
        u.setRole(role);
        u.setActive(true);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return u;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private ResponseEntity<byte[]> download(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        return rest.exchange("/api/v1/files/" + fileId + "/download",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    /**
     * E3-08: TEAM_MEMBER artık dosya indirebilir (matris genişletildi — ekip üyeleri
     * yükledikleri dosyalara erişebilmeli). Eskiden 403 beklenirdi; yeni sözleşmede 200.
     */
    @Test
    void teamMemberCanDownload() {
        ResponseEntity<byte[]> resp = download(memberEmail);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** ADMIN → indirilebilir (200). */
    @Test
    void adminCanDownload() {
        ResponseEntity<byte[]> resp = download(adminEmail);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Kimlik doğrulamasız → 401. */
    @Test
    void unauthenticatedGets401() {
        ResponseEntity<String> resp = rest.getForEntity(
                "/api/v1/files/" + fileId + "/download", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------------
    // E1 review #1: POST /api/v1/files (generic/admin upload ucu) artık ADMIN/ACCOUNTING
    // rol-kapısına tabidir. (E3-05 self-service yolu POST /api/v1/invoices AYRIDIR ve
    // TEAM_MEMBER'a açık kalır — burada test edilmez.)
    // ------------------------------------------------------------------------

    private ResponseEntity<String> uploadAs(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource("UPLOAD-BYTES".getBytes()) {
            @Override
            public String getFilename() {
                return "upload_test.pdf";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        // invoiceId mevcut değil → ADMIN için akış yetki kapısını GEÇER, sonra
        // STORAGE_ERROR (Invoice bulunamadı) ile düşer; önemli olan 403 OLMAMASIDIR.
        body.add("invoiceId", "999999");
        body.add("invoiceDate", "2026-03-01");
        body.add("serviceName", "Auth Test Service");

        return rest.postForEntity("/api/v1/files",
                new HttpEntity<>(body, headers), String.class);
    }

    /** Kimliği doğrulanmış TEAM_MEMBER → 403 (rol kapısı, gövde değerlendirilmeden). */
    @Test
    void teamMemberCannotUpload() {
        ResponseEntity<String> resp = uploadAs(memberEmail);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** ADMIN rol kapısını geçer → 403 DEĞİL (geçersiz invoiceId nedeniyle 400 olur). */
    @Test
    void adminPassesUploadRoleGate() {
        ResponseEntity<String> resp = uploadAs(adminEmail);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * E3 deep-review #6 — POST /files'ta 10MB iş kuralı uygulanır. 10MB'ı aşan dosya
     * (servlet 25MB sınırının ALTINDA ama iş sınırının ÜSTÜNDE) → 400 (sessizce depolanmaz).
     * ADMIN ile gönderilir ki rol-kapısı değil boyut kapısı test edilsin.
     */
    @Test
    void adminOversizedFileGets400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(adminEmail));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // 10MB + 1 byte → iş kuralını aşar (servlet 25MB'a takılmaz).
        byte[] big = new byte[(int) (10L * 1024 * 1024 + 1)];
        ByteArrayResource fileResource = new ByteArrayResource(big) {
            @Override
            public String getFilename() {
                return "too_big.pdf";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("invoiceId", "999999");
        body.add("invoiceDate", "2026-03-01");
        body.add("serviceName", "Oversize Test");

        ResponseEntity<String> resp = rest.postForEntity("/api/v1/files",
                new HttpEntity<>(body, headers), String.class);
        // 400 STORAGE_ERROR (mesaj GlobalExceptionHandler'da sanitize edilir → "10MB" sızmaz).
        // Boyut kapısı, invoice araması/depolamadan ÖNCE çalışır → 400 (sessiz 25MB depolama değil).
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("STORAGE_ERROR");
        // Sadece seed edilen tek FileAsset kalmalı — oversize dosya kaydedilmedi.
        assertThat(fileAssetRepository.findAll()).hasSize(1);
    }

    /**
     * Eksik "file" part'ı olan multipart isteği → istemci hatası 400 (önceden 500'dü).
     * MissingServletRequestPartException artık GlobalExceptionHandler'da 400'e map edilir.
     */
    @Test
    void adminMissingFilePartGets400NotServerError() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(adminEmail));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // "file" part'ı kasıtlı olarak yok — yalnızca diğer alanlar gönderilir.
        body.add("invoiceId", "999999");
        body.add("invoiceDate", "2026-03-01");
        body.add("serviceName", "Auth Test Service");

        ResponseEntity<String> resp = rest.postForEntity("/api/v1/files",
                new HttpEntity<>(body, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
