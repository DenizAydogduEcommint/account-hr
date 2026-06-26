package com.ecommint.accounthr.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.repository.TeamRepository;
import com.ecommint.accounthr.service.importer.StatusColors;

/**
 * E3-03 — {@code GET /api/v1/expenses} integration test.
 *
 * <p>HTTP üzerinden (TestRestTemplate + JWT, {@link com.ecommint.accounthr.dashboard.DashboardSummaryIT}
 * deseni) tam controller + security + servis zincirini doğrular. Seed tek period
 * "2026-07" (gerçek kodlarla çakışmaz); {@link AbstractDataCleanupIT} her test
 * öncesi/sonrası truncate ile izole eder, herhangi bir surefire sırasında geçer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExpenseListIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final String MONTH = "2026-07";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ServiceContactRepository serviceContactRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;

    /** Ana harcama TL toplamı: 100 + 250.50 + 75 = 425.50 (informational hariç). */
    private static final BigDecimal EXPECTED_OPERATIONAL_TRY = new BigDecimal("425.50");
    /** Bilgi-amaçlı (informational) toplam. */
    private static final BigDecimal EXPECTED_INFORMATIONAL_TRY = new BigDecimal("9999.00");

    @BeforeEach
    void seed() {
        AppUser admin = new AppUser();
        adminEmail = "exp.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Harcama Yönetici");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        Provider anthropic = new Provider();
        anthropic.setName("Anthropic");
        providerRepository.save(anthropic);

        Provider multinet = new Provider();
        multinet.setName("Multinet");
        providerRepository.save(multinet);

        Card card3800 = new Card();
        card3800.setBank("Akbank");
        card3800.setLastFour("3800");
        card3800.setHolderName("Kaan Bingöl");
        cardRepository.save(card3800);

        Card card3909 = new Card();
        card3909.setBank("YKB");
        card3909.setLastFour("3909");
        card3909.setHolderName("Kaan Bingöl");
        cardRepository.save(card3909);

        Team backend = new Team();
        backend.setName("Backend");
        teamRepository.save(backend);

        com.ecommint.accounthr.domain.Service claude = new com.ecommint.accounthr.domain.Service();
        claude.setName("Claude AI");
        claude.setProvider(anthropic);
        claude.setFrequency(Frequency.MONTHLY);
        claude.setActiveState(ActiveState.YES);
        claude.setInformational(false);
        serviceRepository.save(claude);

        // Birincil + ikincil iletişim — accountingEmail birincilden gelmeli.
        ServiceContact secondary = new ServiceContact();
        secondary.setService(claude);
        secondary.setEmail("noreply@anthropic.com");
        secondary.setPrimary(false);
        serviceContactRepository.save(secondary);
        ServiceContact primary = new ServiceContact();
        primary.setService(claude);
        primary.setEmail("accounting@e-commint.com");
        primary.setPrimary(true);
        serviceContactRepository.save(primary);

        com.ecommint.accounthr.domain.Service multinetSvc = new com.ecommint.accounthr.domain.Service();
        multinetSvc.setName("Multinet Yemek Kartı");
        multinetSvc.setProvider(multinet);
        multinetSvc.setFrequency(Frequency.MONTHLY);
        multinetSvc.setActiveState(ActiveState.YES);
        multinetSvc.setInformational(true);
        serviceRepository.save(multinetSvc);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(7);
        period.setCode(MONTH);
        periodRepository.save(period);

        // ANA harcamalar (informational=false): 3 satır, kart 3800.
        Expense main1 = mainExpense(claude, period, card3800, backend, new BigDecimal("100.00"));
        Expense main2 = mainExpense(claude, period, card3800, backend, new BigDecimal("250.50"));
        // main3 farklı kart (3909) — kart filtresini ayırt etmek için.
        Expense main3 = mainExpense(claude, period, card3909, backend, new BigDecimal("75.00"));
        expenseRepository.save(main1);
        expenseRepository.save(main2);
        expenseRepository.save(main3);

        // Bilgi-amaçlı (informational=true): operasyonel toplama/sayfaya girmemeli.
        Expense info = new Expense();
        info.setService(multinetSvc);
        info.setPeriod(period);
        info.setTransactionDate(LocalDate.of(2026, 7, 10));
        info.setCurrency(Currency.TRY);
        info.setAmount(EXPECTED_INFORMATIONAL_TRY);
        info.setAmountTry(EXPECTED_INFORMATIONAL_TRY);
        info.setInformational(true);
        expenseRepository.save(info);

        // Temsilci invoice = en güncel (en yüksek id). main1: önce EXPECTED, sonra FOUND.
        invoiceRepository.save(invoice(main1, InvoiceStatus.EXPECTED, null));
        invoiceRepository.save(invoice(main1, InvoiceStatus.FOUND, "faturalar/2026-07/claude.pdf"));
        invoiceRepository.save(invoice(main2, InvoiceStatus.EXPECTED, null));
        invoiceRepository.save(invoice(main3, InvoiceStatus.TO_INVESTIGATE, null));
        invoiceRepository.save(invoice(info, InvoiceStatus.IGNORED, null));
    }

    private Expense mainExpense(com.ecommint.accounthr.domain.Service service, Period period,
            Card card, Team team, BigDecimal amountTry) {
        Expense e = new Expense();
        e.setService(service);
        e.setPeriod(period);
        e.setCard(card);
        e.setUsingTeam(team);
        e.setTransactionDate(LocalDate.of(2026, 7, 15));
        e.setAmount(amountTry);
        e.setCurrency(Currency.TRY);
        e.setAmountTry(amountTry);
        e.setPurpose("LLM API");
        e.setInformational(false);
        return e;
    }

    private Invoice invoice(Expense expense, InvoiceStatus status, String note) {
        Invoice inv = new Invoice();
        inv.setExpense(expense);
        inv.setStatus(status);
        inv.setNote(note);
        return inv;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String adminToken() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", adminEmail, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    @SuppressWarnings("rawtypes")
    private Map list(String queryString) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/expenses" + queryString,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mainPage(Map<String, Object> body) {
        return (Map<String, Object>) body.get("main");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mainContent(Map<String, Object> body) {
        return (List<Map<String, Object>>) mainPage(body).get("content");
    }

    @Test
    @SuppressWarnings("unchecked")
    void populatedMonthReturnsMainRowsInfoSeparatedAndTotals() {
        Map<String, Object> body = list("?month=" + MONTH);

        assertThat(body.get("month")).isEqualTo(MONTH);

        // content yalnızca ANA satırlar (3); informational content'te DEĞİL.
        List<Map<String, Object>> content = mainContent(body);
        assertThat(content).hasSize(3);
        assertThat(content).allSatisfy(row ->
                assertThat(row.get("serviceName")).isEqualTo("Claude AI"));

        // Operasyonel toplam informational hariç.
        assertThat(new BigDecimal(body.get("operationalTotalTry").toString()))
                .isEqualByComparingTo(EXPECTED_OPERATIONAL_TRY);

        // Bilgi-amaçlı satırlar AYRI listede + kendi alt toplamı.
        List<Map<String, Object>> infoRows = (List<Map<String, Object>>) body.get("informationalRows");
        assertThat(infoRows).hasSize(1);
        assertThat(infoRows.get(0).get("serviceName")).isEqualTo("Multinet Yemek Kartı");
        assertThat(new BigDecimal(body.get("informationalTotalTry").toString()))
                .isEqualByComparingTo(EXPECTED_INFORMATIONAL_TRY);

        // 12 kolon alanları + temsilci invoice (en güncel = FOUND) main1'de.
        Map<String, Object> first = content.stream()
                .filter(r -> new BigDecimal(r.get("amountTry").toString())
                        .compareTo(new BigDecimal("100.00")) == 0)
                .findFirst().orElseThrow();
        assertThat(first.get("providerName")).isEqualTo("Anthropic");
        assertThat(first.get("currency")).isEqualTo("TRY");
        assertThat(first.get("cardLast4")).isEqualTo("3800");
        assertThat(first.get("usingTeam")).isEqualTo("Backend");
        assertThat(first.get("purpose")).isEqualTo("LLM API");
        assertThat(first.get("accountingEmail")).isEqualTo("accounting@e-commint.com");
        assertThat(first.get("invoiceStatus")).isEqualTo("FOUND");
        assertThat(first.get("invoiceColorHex")).isEqualTo(StatusColors.STATUS_TO_HEX.get(InvoiceStatus.FOUND));
        assertThat(first.get("invoiceNote")).isEqualTo("faturalar/2026-07/claude.pdf");
    }

    @Test
    void filterByCardReturnsOnlyThatCardsMainRows() {
        Map<String, Object> body = list("?month=" + MONTH + "&card=3909");
        List<Map<String, Object>> content = mainContent(body);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("cardLast4")).isEqualTo("3909");
        // operasyonel toplam dönem geneli kalır (filtreden bağımsız).
        assertThat(new BigDecimal(body.get("operationalTotalTry").toString()))
                .isEqualByComparingTo(EXPECTED_OPERATIONAL_TRY);
    }

    @Test
    void filterByStatusReturnsMatchingMainRows() {
        // TO_INVESTIGATE yalnızca main3'te.
        Map<String, Object> body = list("?month=" + MONTH + "&status=TO_INVESTIGATE");
        List<Map<String, Object>> content = mainContent(body);
        assertThat(content).hasSize(1);
        assertThat(new BigDecimal(content.get(0).get("amountTry").toString()))
                .isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void statusFilterMatchesRepresentativeInvoiceNotAnyInvoice() {
        // main1 invoice geçmişi: önce EXPECTED, sonra FOUND → temsilci (max-id) = FOUND.
        // Rozet FOUND gösterir; bu yüzden ?status=FOUND main1'i DÖNDÜRMELİ,
        // ?status=EXPECTED ise (main1'in ESKİ bir EXPECTED invoice'u OLSA da, temsilci
        // değil) main1'i DÖNDÜRMEMELİ. Aksi halde rozet/filtre tutarsızlığı oluşur.
        BigDecimal main1Amount = new BigDecimal("100.00");

        // ?status=FOUND → temsilcisi FOUND olan main1 döner (yalnızca o).
        List<Map<String, Object>> foundRows = mainContent(list("?month=" + MONTH + "&status=FOUND"));
        assertThat(foundRows).hasSize(1);
        assertThat(new BigDecimal(foundRows.get(0).get("amountTry").toString()))
                .isEqualByComparingTo(main1Amount);
        assertThat(foundRows.get(0).get("invoiceStatus")).isEqualTo("FOUND");

        // ?status=EXPECTED → main1 (eski EXPECTED invoice'u var ama temsilci FOUND) DAHİL DEĞİL.
        // Temsilcisi EXPECTED olan tek satır main2 (75.00 değil; 250.50).
        List<Map<String, Object>> expectedRows = mainContent(list("?month=" + MONTH + "&status=EXPECTED"));
        assertThat(expectedRows).hasSize(1);
        assertThat(new BigDecimal(expectedRows.get(0).get("amountTry").toString()))
                .isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(expectedRows)
                .noneSatisfy(row -> assertThat(new BigDecimal(row.get("amountTry").toString()))
                        .isEqualByComparingTo(main1Amount));
    }

    @Test
    void freeTextSearchMatchesServiceOrProviderCaseInsensitive() {
        // "anthro" sağlayıcı adından eşleşmeli (case-insensitive) → 3 ana satır.
        assertThat(mainContent(list("?month=" + MONTH + "&q=anthro"))).hasSize(3);
        // "claude" hizmet adından → 3 ana satır.
        assertThat(mainContent(list("?month=" + MONTH + "&q=CLAUDE"))).hasSize(3);
        // eşleşmeyen → boş.
        assertThat(mainContent(list("?month=" + MONTH + "&q=zzz-nomatch"))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownMonthReturnsEmptyContentAndZeroTotals() {
        Map<String, Object> body = list("?month=2099-12");
        assertThat(body.get("month")).isEqualTo("2099-12");
        assertThat(mainContent(body)).isEmpty();
        assertThat(new BigDecimal(body.get("operationalTotalTry").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((List<Map<String, Object>>) body.get("informationalRows")).isEmpty();
        assertThat(new BigDecimal(body.get("informationalTotalTry").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void malformedMonthReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/expenses?month=garbage",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("INVALID_MONTH");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void invalidStatusReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/expenses?month=" + MONTH + "&status=GARBAGE",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void unauthenticatedReturns401() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.getForEntity(
                "/api/v1/expenses?month=" + MONTH, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
