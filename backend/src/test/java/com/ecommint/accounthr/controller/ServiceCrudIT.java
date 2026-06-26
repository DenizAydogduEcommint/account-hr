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
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E3-02 Servisler ekranı CRUD entegrasyon testleri.
 *
 * <p>{@link AbstractDataCleanupIT}'i extend eder → her test öncesi/sonrası tüm
 * tablolar FK-güvenli temizlenir; CI alfabetik test sırasında veri sızıntısı yok.
 *
 * <p>Kapsam: liste (seeded), filtre (active), arama (isim), create (provider/card
 * çözümü + persist), update (mutasyon), PATCH active (toggle), geçersiz e-posta →
 * 400, DELETE endpoint YOK (405).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ServiceCrudIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;

    @BeforeEach
    void seed() {
        AppUser admin = new AppUser();
        adminEmail = "crud.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Servis Yönetici");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        Card akbank = new Card();
        akbank.setBank("Akbank");
        akbank.setLastFour("3800");
        akbank.setHolderName("Kaan Bingöl");
        akbank.setLabel("Akbank Axess");
        cardRepository.save(akbank);

        Provider anthropic = new Provider();
        anthropic.setName("Anthropic");
        providerRepository.save(anthropic);

        com.ecommint.accounthr.domain.Service claude = new com.ecommint.accounthr.domain.Service();
        claude.setName("Claude AI");
        claude.setProvider(anthropic);
        claude.setDefaultCard(akbank);
        claude.setFrequency(Frequency.MONTHLY);
        claude.setActiveState(ActiveState.YES);
        claude.setInformational(false);
        serviceRepository.save(claude);

        Provider aws = new Provider();
        aws.setName("AWS");
        providerRepository.save(aws);

        com.ecommint.accounthr.domain.Service awsSvc = new com.ecommint.accounthr.domain.Service();
        awsSvc.setName("AWS Cloud");
        awsSvc.setProvider(aws);
        awsSvc.setFrequency(Frequency.USAGE_BASED);
        awsSvc.setActiveState(ActiveState.NO);
        awsSvc.setInformational(false);
        serviceRepository.save(awsSvc);
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

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void listReturnsSeededServices() {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/services?size=50",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(2L);
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        // Zengin ServiceResponse alanları: activeMonths + contacts dahil, entity sızmaz.
        Map<String, Object> first = content.get(0);
        assertThat(first).containsKeys("name", "providerName", "cardLast4", "activeState", "contacts");
        assertThat(first).doesNotContainKeys("provider", "defaultCard", "usingTeam");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void filterByActiveReturnsOnlyMatching() {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/services?active=YES",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(1L);
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content.get(0).get("name")).isEqualTo("Claude AI");
        assertThat(content.get(0).get("activeState")).isEqualTo("YES");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void searchByNameMatchesCaseInsensitive() {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/services?q=claude",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(1L);
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content.get(0).get("name")).isEqualTo("Claude AI");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void searchByProviderMatches() {
        ResponseEntity<Map> resp = rest.exchange("/api/v1/services?q=Anthropic",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        Map<String, Object> body = resp.getBody();
        assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(1L);
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content.get(0).get("providerName")).isEqualTo("Anthropic");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createPersistsAndResolvesProviderAndCard() {
        Map<String, Object> payload = Map.of(
                "name", "Zoom",
                "providerName", "Zoom Video", // yeni provider → resolve-or-create
                "cardLast4", "3800",            // seeded Akbank karta eşlenir
                "frequency", "MONTHLY",
                "activeState", "YES",
                "approxAmountTry", 500.00,
                "invoiceSource", "EMAIL",
                "contacts", List.of(Map.of("email", "billing@zoom.us", "primary", true)));

        ResponseEntity<Map> resp = rest.exchange("/api/v1/services",
                HttpMethod.POST, new HttpEntity<>(payload, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("name")).isEqualTo("Zoom");
        assertThat(body.get("providerName")).isEqualTo("Zoom Video");
        assertThat(body.get("cardLast4")).isEqualTo("3800");
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) body.get("contacts");
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).get("email")).isEqualTo("billing@zoom.us");
        assertThat(contacts.get(0).get("primary")).isEqualTo(true);

        // Provider gerçekten oluştu, servis kalıcı.
        assertThat(providerRepository.findByNameIgnoreCase("Zoom Video")).isPresent();
        assertThat(serviceRepository.count()).isEqualTo(3L);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void updateMutatesService() {
        Long id = serviceRepository.findAll().stream()
                .filter(s -> s.getName().equals("Claude AI")).findFirst().orElseThrow().getId();

        Map<String, Object> payload = Map.of(
                "name", "Claude AI Pro",
                "providerName", "Anthropic",
                "frequency", "MONTHLY",
                "activeState", "YES",
                "notes", "Güncellendi",
                "contacts", List.of(Map.of("email", "a@x.com, b@y.com")));

        ResponseEntity<Map> resp = rest.exchange("/api/v1/services/" + id,
                HttpMethod.PUT, new HttpEntity<>(payload, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("name")).isEqualTo("Claude AI Pro");
        assertThat(body.get("notes")).isEqualTo("Güncellendi");
        List<Map<String, Object>> contacts = (List<Map<String, Object>>) body.get("contacts");
        assertThat(contacts).hasSize(1);
        // Virgüllü çoklu adres verbatim saklanır.
        assertThat(contacts.get(0).get("email")).isEqualTo("a@x.com, b@y.com");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void patchActiveToggles() {
        Long id = serviceRepository.findAll().stream()
                .filter(s -> s.getName().equals("Claude AI")).findFirst().orElseThrow().getId();

        ResponseEntity<Map> resp = rest.exchange("/api/v1/services/" + id + "/active",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("activeState", "NO"), authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("activeState")).isEqualTo("NO");
        assertThat(serviceRepository.findById(id).orElseThrow().getActiveState())
                .isEqualTo(ActiveState.NO);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void invalidEmailReturns400() {
        Map<String, Object> payload = Map.of(
                "name", "Bad Service",
                "providerName", "X",
                "contacts", List.of(Map.of("email", "not-an-email")));

        ResponseEntity<Map> resp = rest.exchange("/api/v1/services",
                HttpMethod.POST, new HttpEntity<>(payload, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
        // Servis oluşmamalı.
        assertThat(serviceRepository.count()).isEqualTo(2L);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void blankNameReturns400() {
        Map<String, Object> payload = Map.of(
                "name", "",
                "providerName", "X");

        ResponseEntity<Map> resp = rest.exchange("/api/v1/services",
                HttpMethod.POST, new HttpEntity<>(payload, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void noHardDeleteEndpoint() {
        Long id = serviceRepository.findAll().get(0).getId();

        ResponseEntity<Map> resp = rest.exchange("/api/v1/services/" + id,
                HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Map.class);

        // DELETE uç noktası tanımlı değil → 405 Method Not Allowed (200/204 OLMAMALI).
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        // Servis hâlâ duruyor.
        assertThat(serviceRepository.existsById(id)).isTrue();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void cardsReferenceEndpointReturnsSeededCard() {
        ResponseEntity<List> resp = rest.exchange("/api/v1/cards",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> cards = resp.getBody();
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).get("last4")).isEqualTo("3800");
        assertThat(cards.get(0).get("bank")).isEqualTo("Akbank");
    }
}
