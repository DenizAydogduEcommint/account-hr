package com.ecommint.accounthr.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.repository.TeamRepository;

/**
 * E3-06 — {@code POST /api/v1/expenses} elle harcama satırı oluşturma integration test.
 *
 * <p>HTTP üzerinden (TestRestTemplate + JWT, {@link ExpenseListIT} deseni) tam controller +
 * security + servis zincirini doğrular. Tek period {@code 2026-08} (gerçek kodlarla
 * çakışmaz) {@code transactionDate}'ten find-or-create edilir. {@link AbstractDataCleanupIT}
 * her test öncesi/sonrası truncate ile izole eder → herhangi bir surefire sırasında geçer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExpenseCreateIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final String MONTH = "2026-08";
    private static final String TX_DATE = "2026-08-15";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private Long serviceId;
    private Long teamId;

    @BeforeEach
    void seed() {
        AppUser admin = new AppUser();
        adminEmail = "exp.create.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Harcama Giren");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        Provider anthropic = new Provider();
        anthropic.setName("Anthropic");
        providerRepository.save(anthropic);

        Card card3800 = new Card();
        card3800.setBank("Akbank");
        card3800.setLastFour("3800");
        card3800.setHolderName("Kaan Bingöl");
        cardRepository.save(card3800);

        Team backend = new Team();
        backend.setName("Backend");
        teamRepository.save(backend);
        teamId = backend.getId();

        // Aktif + Aylık servis → eksik fatura çapraz doğrulamasının kontrol kümesinde.
        // Varsayılan kartı 3800 (cardLast4 verilmediğinde buna düşülmeli).
        com.ecommint.accounthr.domain.Service claude = new com.ecommint.accounthr.domain.Service();
        claude.setName("Claude AI");
        claude.setProvider(anthropic);
        claude.setDefaultCard(card3800);
        claude.setFrequency(Frequency.MONTHLY);
        claude.setActiveState(ActiveState.YES);
        claude.setInformational(false);
        serviceRepository.save(claude);
        serviceId = claude.getId();
    }

    // ---------------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String adminToken() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", adminEmail, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders authJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ResponseEntity<Map> post(Map<String, Object> body) {
        return rest.exchange("/api/v1/expenses", HttpMethod.POST,
                new HttpEntity<>(body, authJson()), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private Map getList(String queryString) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/expenses" + queryString, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mainContent(Map<String, Object> body) {
        Map<String, Object> main = (Map<String, Object>) body.get("main");
        return (List<Map<String, Object>>) main.get("content");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> missingRows() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        // E3-10: yanıt artık {items, count, approxTotalTry} zarfı.
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/missing-invoices?month=" + MONTH, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) resp.getBody().get("items");
    }

    private Map<String, Object> baseRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("serviceId", serviceId);
        body.put("transactionDate", TX_DATE);
        body.put("amount", 20.00);
        body.put("currency", "USD");
        body.put("amountTry", 680.50);
        return body;
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void createsManualRowAppearsInGetAsExpectedAndManualWithOperationalTotal() {
        Map<String, Object> req = baseRequest();
        req.put("usingTeamId", teamId);
        req.put("purpose", "LLM API");
        req.put("informational", false);

        ResponseEntity<Map> created = post(req);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> row = created.getBody();
        assertThat(row).isNotNull();

        // Yanıt = GET ile aynı ExpenseRow şekli.
        assertThat(row.get("serviceName")).isEqualTo("Claude AI");
        assertThat(row.get("providerName")).isEqualTo("Anthropic");
        assertThat(row.get("currency")).isEqualTo("USD");
        assertThat(row.get("cardLast4")).isEqualTo("3800"); // servisin varsayılan kartına düştü
        assertThat(row.get("usingTeam")).isEqualTo("Backend");
        assertThat(row.get("purpose")).isEqualTo("LLM API");
        assertThat(row.get("invoiceStatus")).isEqualTo("EXPECTED"); // Bekleniyor
        assertThat(row.get("source")).isEqualTo("MANUAL");
        assertThat(new BigDecimal(row.get("amount").toString()))
                .isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(new BigDecimal(row.get("amountTry").toString()))
                .isEqualByComparingTo(new BigDecimal("680.50"));

        // GET listesinde görünür (aynı ay), EXPECTED + MANUAL.
        Map<String, Object> body = getList("?month=" + MONTH);
        List<Map<String, Object>> content = mainContent(body);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("source")).isEqualTo("MANUAL");
        assertThat(content.get(0).get("invoiceStatus")).isEqualTo("EXPECTED");

        // Operasyonel toplam satırı yansıtır (informational=false).
        assertThat(new BigDecimal(body.get("operationalTotalTry").toString()))
                .isEqualByComparingTo(new BigDecimal("680.50"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void informationalManualRowExcludedFromOperationalTotal() {
        Map<String, Object> req = baseRequest();
        req.put("informational", true);

        assertThat(post(req).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = getList("?month=" + MONTH);
        // ANA satırlarda DEĞİL (informational → ayrı liste).
        assertThat(mainContent(body)).isEmpty();
        // Operasyonel toplam DAHİL ETMEZ.
        assertThat(new BigDecimal(body.get("operationalTotalTry").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        // Bilgi-amaçlı listede + alt toplamda var.
        List<Map<String, Object>> infoRows = (List<Map<String, Object>>) body.get("informationalRows");
        assertThat(infoRows).hasSize(1);
        assertThat(infoRows.get(0).get("source")).isEqualTo("MANUAL");
        assertThat(new BigDecimal(body.get("informationalTotalTry").toString()))
                .isEqualByComparingTo(new BigDecimal("680.50"));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void cardLast4WhenProvidedIsUsed() {
        Card card3909 = new Card();
        card3909.setBank("YKB");
        card3909.setLastFour("3909");
        cardRepository.save(card3909);

        Map<String, Object> req = baseRequest();
        req.put("cardLast4", "3909");
        ResponseEntity<Map> created = post(req);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("cardLast4")).isEqualTo("3909");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void missingRequiredFieldReturns400() {
        Map<String, Object> req = baseRequest();
        req.remove("amountTry"); // @NotNull ihlali
        ResponseEntity<Map> resp = post(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void nonPositiveAmountReturns400() {
        Map<String, Object> req = baseRequest();
        req.put("amount", 0); // @Positive ihlali
        ResponseEntity<Map> resp = post(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void unknownServiceIdReturns404() {
        Map<String, Object> req = baseRequest();
        req.put("serviceId", 999999L);
        ResponseEntity<Map> resp = post(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void unknownCardLast4Returns400() {
        Map<String, Object> req = baseRequest();
        req.put("cardLast4", "0000"); // bilinmeyen kart
        ResponseEntity<Map> resp = post(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void unknownTeamIdReturns400() {
        Map<String, Object> req = baseRequest();
        req.put("usingTeamId", 999999L); // bilinmeyen takım
        ResponseEntity<Map> resp = post(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void unauthenticatedReturns401() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/expenses", baseRequest(), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * E3-04 semantik doğrulaması — Elle bir EXPECTED satırı oluşturmak, servisi eksik
     * listesinden DÜŞÜRMEZ. "Eksik" tanımı invoice durumudur: yalnızca FOUND/E_INVOICE
     * bir servisi "bulundu" yapar. EXPECTED (Bekleniyor) = henüz fatura YOK = hâlâ eksik.
     * Bu, {@link com.ecommint.accounthr.service.MissingInvoiceService} ile birebir tutarlıdır
     * ve E3-06 ile çelişmez (elle satır girilse bile fatura toplanana kadar eksik görünür).
     */
    @Test
    void manualExpectedRowStillCountsAsMissing() {
        // Başlangıçta period 2026-08 DB'de yok (henüz hiç satır/import yok); MissingInvoiceService
        // bilinmeyen period için toleranslı boş liste döner (dashboard ile aynı). Period, ilk
        // elle satır POST'unda find-or-create edilir; asıl iddia POST SONRASIDIR.

        // Elle EXPECTED satırı oluştur (period'u da yaratır).
        Map<String, Object> req = baseRequest();
        assertThat(post(req).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Satır artık GET listesinde EXPECTED olarak görünür...
        assertThat(mainContent(getList("?month=" + MONTH))).hasSize(1);

        // ...AMA servis HÂLÂ eksik: "eksik" = FOUND/E_INVOICE invoice yok. EXPECTED
        // (Bekleniyor) = henüz fatura yok = eksik (MissingInvoiceService ile birebir).
        List<Map<String, Object>> after = missingRows();
        assertThat(after).anySatisfy(r ->
                assertThat(r.get("serviceName")).isEqualTo("Claude AI"));
    }
}
