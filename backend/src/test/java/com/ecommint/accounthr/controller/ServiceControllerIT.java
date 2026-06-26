package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.RefreshTokenRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.repository.TeamRepository;

/**
 * E1-07 referans endpoint testi: {@code GET /api/v1/services}.
 *
 * <ul>
 *   <li>Kimlik doğrulanmış istek → 200 + standart PagedResponse şekli.</li>
 *   <li>Kimliksiz istek → 401 + standart ErrorResponse şekli (traceId alanı dahil).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ServiceControllerIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private Long engineeringTeamId;

    @BeforeEach
    void seed() {
        serviceRepository.deleteAll();
        providerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        AppUser admin = new AppUser();
        adminEmail = "svc.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Servis Yönetici");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        Provider anthropic = new Provider();
        anthropic.setName("Anthropic");
        providerRepository.save(anthropic);

        Team engineering = new Team();
        engineering.setName("Engineering");
        teamRepository.save(engineering);
        engineeringTeamId = engineering.getId();

        // Takımı OLAN servis → usingTeamId = takımın id'si.
        com.ecommint.accounthr.domain.Service svc = new com.ecommint.accounthr.domain.Service();
        svc.setName("Claude AI");
        svc.setProvider(anthropic);
        svc.setUsingTeam(engineering);
        svc.setFrequency(Frequency.MONTHLY);
        svc.setActiveState(ActiveState.YES);
        svc.setInformational(false);
        serviceRepository.save(svc);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String adminToken() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", adminEmail, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void listServicesAuthenticatedReturnsPagedShape() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());

        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/services?page=0&size=20&sort=name,asc",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        // PagedResponse zarf alanları
        assertThat(body).containsKeys(
                "content", "page", "size", "totalElements", "totalPages", "first", "last", "sort");
        assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(1L);
        assertThat((Integer) body.get("page")).isEqualTo(0);
        assertThat((Integer) body.get("size")).isEqualTo(20);

        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).hasSize(1);
        Map<String, Object> dto = content.get(0);
        assertThat(dto.get("name")).isEqualTo("Claude AI");
        assertThat(dto.get("providerName")).isEqualTo("Anthropic");
        assertThat(dto.get("frequency")).isEqualTo("MONTHLY");
        assertThat(dto.get("activeState")).isEqualTo("YES");
        // Takımı olan servis → usingTeamName + usingTeamId her ikisi de dolu.
        assertThat(dto.get("usingTeamName")).isEqualTo("Engineering");
        assertThat(((Number) dto.get("usingTeamId")).longValue()).isEqualTo(engineeringTeamId);
        // Entity sızmamalı: ilişkisel nesneler yerine düz alanlar
        assertThat(dto).doesNotContainKeys("provider", "defaultCard", "usingTeam");
    }

    // E3-06: takımı OLMAYAN servis → usingTeamId null döner (dropdown ön-seçimi yok).
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void serviceWithoutTeamHasNullUsingTeamId() {
        Provider aws = new Provider();
        aws.setName("Amazon");
        providerRepository.save(aws);

        com.ecommint.accounthr.domain.Service teamless = new com.ecommint.accounthr.domain.Service();
        teamless.setName("AWS");
        teamless.setProvider(aws);
        teamless.setFrequency(Frequency.USAGE_BASED);
        teamless.setActiveState(ActiveState.YES);
        teamless.setInformational(false);
        serviceRepository.save(teamless);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());

        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/services?q=AWS&size=20",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.getBody().get("content");
        assertThat(content).hasSize(1);
        Map<String, Object> dto = content.get(0);
        assertThat(dto.get("name")).isEqualTo("AWS");
        assertThat(dto).containsKey("usingTeamId"); // alan mevcut...
        assertThat(dto.get("usingTeamId")).isNull(); // ...ama takım olmadığı için null
        assertThat(dto.get("usingTeamName")).isNull();
    }

    // E1 review #3: ?size çok büyük olsa bile (allocation-DoS) Spring sayfa boyutunu
    // spring.data.web.pageable.max-page-size (=100) ile kırpar. size=99999 → page.size <= 100.
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void oversizedPageSizeIsCappedAt100() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());

        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/services?size=99999",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        // Etkin sayfa boyutu 100'e kırpılmalı (99999 DEĞİL).
        assertThat(((Number) body.get("size")).intValue()).isLessThanOrEqualTo(100);
        // İçerik de hiçbir zaman 100 satırı aşmamalı.
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content.size()).isLessThanOrEqualTo(100);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void listServicesUnauthenticatedReturns401InErrorResponseShape() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/v1/services", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        // Standart ErrorResponse alanları
        assertThat(body).containsKeys("timestamp", "status", "error", "message", "path");
        assertThat(body).containsKey("traceId"); // null olabilir ama alan mevcut olmalı
        assertThat(((Number) body.get("status")).intValue()).isEqualTo(401);
        assertThat(body.get("error")).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("path")).isEqualTo("/api/v1/services");
    }
}
