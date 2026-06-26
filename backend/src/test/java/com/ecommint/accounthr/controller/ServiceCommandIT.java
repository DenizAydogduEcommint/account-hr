package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E3-02 kod-incelemesi düzeltmeleri için IT'ler:
 *
 * <ul>
 *   <li>Geçersiz enum query parametresi ({@code ?active=GARBAGE}) → 500 yerine
 *       400 VALIDATION_ERROR (standart {@code ErrorResponse} şekli).</li>
 *   <li>PUT gövdesinde {@code informational} alanı yoksa, var olan {@code true}
 *       değeri sessizce false'a sıfırlanmaz — korunur (veri kaybı düzeltmesi).</li>
 * </ul>
 *
 * <p>{@link AbstractDataCleanupIT}'i genişletir; her surefire sırasında temiz.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ServiceCommandIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;

    @BeforeEach
    void seed() {
        AppUser admin = new AppUser();
        adminEmail = "svc.cmd.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Servis Yönetici");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String adminToken() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", adminEmail, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        return headers;
    }

    /** Var olan, informational=true bir servis tohumlar; id döner. */
    private Long seedInformationalService() {
        Provider provider = new Provider();
        provider.setName("Multinet");
        providerRepository.save(provider);

        com.ecommint.accounthr.domain.Service svc = new com.ecommint.accounthr.domain.Service();
        svc.setName("Multinet Yemek Kartı");
        svc.setProvider(provider);
        svc.setFrequency(Frequency.MONTHLY);
        svc.setActiveState(ActiveState.YES);
        svc.setInformational(true);
        return serviceRepository.save(svc).getId();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void invalidEnumQueryParamReturns400ValidationError() {
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/services?active=GARBAGE",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        // Standart ErrorResponse şekli (traceId alanı dahil).
        assertThat(body).containsKeys("status", "error", "message", "path", "traceId");
        assertThat(((Number) body.get("status")).intValue()).isEqualTo(400);
        assertThat(body.get("error")).isEqualTo("VALIDATION_ERROR");
        assertThat((String) body.get("message")).contains("GARBAGE").contains("active");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void invalidFrequencyQueryParamReturns400ValidationError() {
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/services?frequency=GARBAGE",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void putWithoutInformationalPreservesExistingTrue() {
        Long id = seedInformationalService();

        // informational alanı OLMAYAN bir güncelleme isteği (null kalır).
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", "Multinet Yemek Kartı");
        body.put("providerName", "Multinet");
        body.put("frequency", "MONTHLY");
        body.put("activeState", "YES");

        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/services/" + id,
                HttpMethod.PUT, new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dto = resp.getBody();
        assertThat(dto).isNotNull();
        // Düzeltme: PUT informational'ı sessizce false'a sıfırlamamalı.
        assertThat(dto.get("informational")).isEqualTo(true);

        // DB tarafında da korunmuş olmalı.
        assertThat(serviceRepository.findById(id))
                .get()
                .extracting(com.ecommint.accounthr.domain.Service::isInformational)
                .isEqualTo(true);
    }
}
