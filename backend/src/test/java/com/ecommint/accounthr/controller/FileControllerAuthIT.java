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

    /** Kimliği doğrulanmış ama yetkisiz TEAM_MEMBER → 403. */
    @Test
    void nonAdminAuthenticatedUserGets403OnDownload() {
        ResponseEntity<byte[]> resp = download(memberEmail);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
}
