package com.ecommint.accounthr.dashboard;

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
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.RefreshTokenRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.service.importer.StatusColors;

/**
 * E3-01 — {@code GET /api/v1/dashboard/summary} integration test.
 *
 * <p>HTTP üzerinden test edilir (TestRestTemplate + JWT, {@link com.ecommint.accounthr.controller.ServiceControllerIT}
 * deseni): tam controller + security + servis aggregation zincirini doğrular.
 * Sayısal kontroller HTTP yanıt gövdesinden okunur. Seed tek period "2026-09"
 * (gerçek kodlarla çakışmaz) ile yapılır; {@link AbstractDataCleanupIT} NOT_SUPPORTED
 * olduğundan repo.save commit eder, her test before/after truncate ile izole edilir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DashboardSummaryIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;

    /** Ana harcama TL toplamı: 100 + 250.50 + 75 = 425.50 (informational hariç). */
    private static final BigDecimal EXPECTED_TOTAL_TRY = new BigDecimal("425.50");
    /** Bilgi-amaçlı (informational) expense tutarı — toplama girMEMELİ. */
    private static final BigDecimal INFORMATIONAL_TRY = new BigDecimal("9999.00");

    @BeforeEach
    void seed() {
        AppUser admin = new AppUser();
        adminEmail = "dash.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Dashboard Yönetici");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        Provider provider = new Provider();
        provider.setName("DashTest Provider");
        providerRepository.save(provider);

        com.ecommint.accounthr.domain.Service service = new com.ecommint.accounthr.domain.Service();
        service.setName("DashTest Service");
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service.setInformational(false);
        serviceRepository.save(service);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(9);
        period.setCode("2026-09");
        periodRepository.save(period);

        // Ana harcamalar (informational=false): 3 satır.
        Expense main1 = mainExpense(service, period, new BigDecimal("100.00"));
        Expense main2 = mainExpense(service, period, new BigDecimal("250.50"));
        Expense main3 = mainExpense(service, period, new BigDecimal("75.00"));
        expenseRepository.save(main1);
        expenseRepository.save(main2);
        expenseRepository.save(main3);

        // Bilgi-amaçlı (informational=true): toplama/expenseCount'a girmemeli.
        Expense info = mainExpense(service, period, INFORMATIONAL_TRY);
        info.setInformational(true);
        expenseRepository.save(info);

        // Invoice'lar: 2 FOUND, 1 E_INVOICE, 3 EXPECTED, 1 TO_INVESTIGATE, 1 IGNORED.
        invoiceRepository.save(invoice(main1, InvoiceStatus.FOUND));
        invoiceRepository.save(invoice(main1, InvoiceStatus.FOUND));
        invoiceRepository.save(invoice(main2, InvoiceStatus.E_INVOICE));
        invoiceRepository.save(invoice(main2, InvoiceStatus.EXPECTED));
        invoiceRepository.save(invoice(main3, InvoiceStatus.EXPECTED));
        invoiceRepository.save(invoice(main3, InvoiceStatus.EXPECTED));
        invoiceRepository.save(invoice(main1, InvoiceStatus.TO_INVESTIGATE));
        // IGNORED informational expense'e bağlı (durum sayımı yine de periyot bazlı).
        invoiceRepository.save(invoice(info, InvoiceStatus.IGNORED));
    }

    private Expense mainExpense(com.ecommint.accounthr.domain.Service service, Period period,
            BigDecimal amountTry) {
        Expense e = new Expense();
        e.setService(service);
        e.setPeriod(period);
        e.setTransactionDate(LocalDate.of(2026, 9, 15));
        e.setAmount(amountTry);
        e.setCurrency(Currency.TRY);
        e.setAmountTry(amountTry);
        e.setInformational(false);
        return e;
    }

    private Invoice invoice(Expense expense, InvoiceStatus status) {
        Invoice inv = new Invoice();
        inv.setExpense(expense);
        inv.setStatus(status);
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
    private Map getSummary(String month) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/dashboard/summary?month=" + month,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void populatedMonthReturnsCorrectKpisAndDistribution() {
        Map<String, Object> body = getSummary("2026-09");

        assertThat(body.get("month")).isEqualTo("2026-09");
        // totalTry: SADECE informational=false toplamı (9999.00 hariç).
        assertThat(new BigDecimal(body.get("totalTry").toString()))
                .isEqualByComparingTo(EXPECTED_TOTAL_TRY);
        // expenseCount: 3 ana harcama (informational hariç).
        assertThat(((Number) body.get("expenseCount")).longValue()).isEqualTo(3L);
        // missingCount artık servis ↔ ay çapraz doğrulamasından (E3-04) gelir, EXPECTED
        // invoice sayısından DEĞİL. Tek aday "DashTest Service" (MONTHLY + YES) bu ay
        // FOUND faturalı harcamaya sahip → eksik DEĞİL → missingCount = 0.
        assertThat(((Number) body.get("missingCount")).longValue()).isEqualTo(0L);
        // foundCount = FOUND(2) + E_INVOICE(1) = 3.
        assertThat(((Number) body.get("foundCount")).longValue()).isEqualTo(3L);
        // investigateCount = TO_INVESTIGATE (1).
        assertThat(((Number) body.get("investigateCount")).longValue()).isEqualTo(1L);

        List<Map<String, Object>> statusCounts = (List<Map<String, Object>>) body.get("statusCounts");
        assertThat(statusCounts).hasSize(5);

        Map<String, Long> byStatus = new java.util.HashMap<>();
        Map<String, String> colorByStatus = new java.util.HashMap<>();
        for (Map<String, Object> sc : statusCounts) {
            String status = (String) sc.get("status");
            byStatus.put(status, ((Number) sc.get("count")).longValue());
            colorByStatus.put(status, (String) sc.get("colorHex"));
        }

        assertThat(byStatus).containsAllEntriesOf(Map.of(
                "FOUND", 2L,
                "E_INVOICE", 1L,
                "EXPECTED", 3L,
                "TO_INVESTIGATE", 1L,
                "IGNORED", 1L));

        // Renk kodları tek kaynak StatusColors ile eşleşmeli.
        for (InvoiceStatus status : InvoiceStatus.values()) {
            assertThat(colorByStatus.get(status.name()))
                    .isEqualTo(StatusColors.STATUS_TO_HEX.get(status));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownMonthReturnsZerosWithoutError() {
        Map<String, Object> body = getSummary("2099-12");

        assertThat(body.get("month")).isEqualTo("2099-12");
        assertThat(new BigDecimal(body.get("totalTry").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(((Number) body.get("expenseCount")).longValue()).isEqualTo(0L);
        assertThat(((Number) body.get("missingCount")).longValue()).isEqualTo(0L);
        assertThat(((Number) body.get("foundCount")).longValue()).isEqualTo(0L);
        assertThat(((Number) body.get("investigateCount")).longValue()).isEqualTo(0L);

        List<Map<String, Object>> statusCounts = (List<Map<String, Object>>) body.get("statusCounts");
        assertThat(statusCounts).hasSize(5);
        for (Map<String, Object> sc : statusCounts) {
            assertThat(((Number) sc.get("count")).longValue()).isEqualTo(0L);
            String status = (String) sc.get("status");
            assertThat((String) sc.get("colorHex"))
                    .isEqualTo(StatusColors.STATUS_TO_HEX.get(InvoiceStatus.valueOf(status)));
        }
    }

    @Test
    void unauthenticatedReturns401() {
        ResponseEntity<Map> resp = rest.getForEntity(
                "/api/v1/dashboard/summary?month=2026-09", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Biçimsiz month → 400 INVALID_MONTH (untrusted girdi doğrulanır, yansıtılmaz). */
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void malformedMonthReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/dashboard/summary?month=garbage",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("INVALID_MONTH");
    }

    /** Geçersiz ay numarası (13) da biçimsiz sayılır → 400 INVALID_MONTH. */
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void invalidMonthNumberReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/dashboard/summary?month=2026-13",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat((String) resp.getBody().get("error")).isEqualTo("INVALID_MONTH");
    }

    /** İyi-biçimli ama boş ay (2026-09 seed period; bu testte seed YOK çünkü truncate) → 200 sıfır. */
    @Test
    @SuppressWarnings("unchecked")
    void wellFormedEmptyMonthReturnsZeros() {
        // 2099-09 hiçbir zaman seed edilmez → iyi-biçimli ama veri yok → 200 sıfır.
        Map<String, Object> body = getSummary("2099-09");
        assertThat(body.get("month")).isEqualTo("2099-09");
        assertThat(new BigDecimal(body.get("totalTry").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(((Number) body.get("expenseCount")).longValue()).isEqualTo(0L);
        List<Map<String, Object>> statusCounts = (List<Map<String, Object>>) body.get("statusCounts");
        assertThat(statusCounts).hasSize(5);
    }

    /** month omitted → 200, echo içinde bulunulan ay (YearMonth.now(), YYYY-MM biçimi). */
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void omittedMonthReturnsCurrentMonth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/dashboard/summary",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        String expected = java.time.YearMonth.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        assertThat((String) resp.getBody().get("month")).isEqualTo(expected);
    }
}
