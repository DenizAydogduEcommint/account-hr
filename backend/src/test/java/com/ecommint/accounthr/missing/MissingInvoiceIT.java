package com.ecommint.accounthr.missing;

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
import org.springframework.core.ParameterizedTypeReference;
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

/**
 * E3-04 — {@code GET /api/v1/missing-invoices} servis ↔ ay çapraz doğrulama integration test.
 *
 * <p>HTTP üzerinden (TestRestTemplate + JWT) tam controller + security + servis zincirini
 * doğrular. Her frekans senaryosu ayrı test edilir (DOD). {@link AbstractDataCleanupIT}
 * before/after truncate ile herhangi bir test sırasında izolasyon sağlar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MissingInvoiceIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    /** Tüm senaryolar tek ay üzerinde kurulur (gerçek kodlarla çakışmaz). */
    private static final String MONTH = "2026-09";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ServiceContactRepository serviceContactRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private Provider provider;
    private Card card;
    private Period period;

    @BeforeEach
    void seedBase() {
        AppUser admin = new AppUser();
        adminEmail = "missing.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Eksik Fatura Yönetici");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        provider = new Provider();
        provider.setName("MissingTest Provider");
        providerRepository.save(provider);

        card = new Card();
        card.setBank("Akbank");
        card.setLastFour("3800");
        cardRepository.save(card);

        period = new Period();
        period.setYear(2026);
        period.setMonth(9);
        period.setCode(MONTH);
        periodRepository.save(period);
    }

    // --- seed helpers -------------------------------------------------------

    private com.ecommint.accounthr.domain.Service service(String name, Frequency freq,
            ActiveState active, boolean informational, String activeMonths) {
        com.ecommint.accounthr.domain.Service s = new com.ecommint.accounthr.domain.Service();
        s.setName(name);
        s.setProvider(provider);
        s.setDefaultCard(card);
        s.setFrequency(freq);
        s.setActiveState(active);
        s.setInformational(informational);
        s.setActiveMonths(activeMonths);
        s.setApproxAmountTry(new BigDecimal("1250.00"));
        return serviceRepository.save(s);
    }

    /** Belirli bir yaklaşık TL tutarı (null olabilir) ile servis ekler. */
    private com.ecommint.accounthr.domain.Service serviceWithApprox(String name, Frequency freq,
            ActiveState active, boolean informational, String activeMonths, BigDecimal approx) {
        com.ecommint.accounthr.domain.Service s = new com.ecommint.accounthr.domain.Service();
        s.setName(name);
        s.setProvider(provider);
        s.setDefaultCard(card);
        s.setFrequency(freq);
        s.setActiveState(active);
        s.setInformational(informational);
        s.setActiveMonths(activeMonths);
        s.setApproxAmountTry(approx);
        return serviceRepository.save(s);
    }

    private void contact(com.ecommint.accounthr.domain.Service s, String email, boolean primary) {
        ServiceContact c = new ServiceContact();
        c.setService(s);
        c.setEmail(email);
        c.setPrimary(primary);
        serviceContactRepository.save(c);
    }

    /** Verilen statüde bir invoice'lu harcama ekler (informational=false). */
    private void expenseWithInvoice(com.ecommint.accounthr.domain.Service s, InvoiceStatus status) {
        expenseWithInvoice(s, status, false);
    }

    /** Verilen statüde bir invoice'lu harcama ekler (informational seçilebilir). */
    private void expenseWithInvoice(com.ecommint.accounthr.domain.Service s, InvoiceStatus status,
            boolean informational) {
        Expense e = new Expense();
        e.setService(s);
        e.setPeriod(period);
        e.setTransactionDate(LocalDate.of(2026, 9, 10));
        e.setAmount(new BigDecimal("100.00"));
        e.setCurrency(Currency.TRY);
        e.setAmountTry(new BigDecimal("100.00"));
        e.setInformational(informational);
        expenseRepository.save(e);

        Invoice inv = new Invoice();
        inv.setExpense(e);
        inv.setStatus(status);
        invoiceRepository.save(inv);
    }

    // --- HTTP helpers -------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String adminToken() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", adminEmail, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    /** Tam yanıt zarfını ({items, count, approxTotalTry}) döner. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMissingResponse(String month) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "/api/v1/missing-invoices?month=" + month,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        return resp.getBody();
    }

    /** Yanıt zarfından yalnızca {@code items} satırlarını döner. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMissing(String month) {
        return (List<Map<String, Object>>) getMissingResponse(month).get("items");
    }

    private List<String> missingNames(String month) {
        return getMissing(month).stream().map(r -> (String) r.get("serviceName")).toList();
    }

    @SuppressWarnings("rawtypes")
    private Map dashboardSummary(String month) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/dashboard/summary?month=" + month,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    // --- frequency scenarios ------------------------------------------------

    /** MONTHLY + YES, FOUND faturalı harcama var → eksik DEĞİL. */
    @Test
    void monthlyActiveWithFoundExpenseIsNotMissing() {
        com.ecommint.accounthr.domain.Service s =
                service("Aylık Bulundu", Frequency.MONTHLY, ActiveState.YES, false, null);
        expenseWithInvoice(s, InvoiceStatus.FOUND);

        assertThat(missingNames(MONTH)).doesNotContain("Aylık Bulundu");
    }

    /** MONTHLY + YES, e-Fatura faturalı harcama var → eksik DEĞİL. */
    @Test
    void monthlyActiveWithEInvoiceExpenseIsNotMissing() {
        com.ecommint.accounthr.domain.Service s =
                service("Aylık e-Fatura", Frequency.MONTHLY, ActiveState.YES, false, null);
        expenseWithInvoice(s, InvoiceStatus.E_INVOICE);

        assertThat(missingNames(MONTH)).doesNotContain("Aylık e-Fatura");
    }

    /** MONTHLY + YES, yalnızca EXPECTED (Bekleniyor) harcama → eksik. */
    @Test
    void monthlyActiveWithOnlyExpectedExpenseIsMissing() {
        com.ecommint.accounthr.domain.Service s =
                service("Aylık Bekleniyor", Frequency.MONTHLY, ActiveState.YES, false, null);
        expenseWithInvoice(s, InvoiceStatus.EXPECTED);

        assertThat(missingNames(MONTH)).contains("Aylık Bekleniyor");
    }

    /** MONTHLY + YES, hiç harcama yok → eksik. */
    @Test
    void monthlyActiveWithNoExpenseIsMissing() {
        service("Aylık Hiç Yok", Frequency.MONTHLY, ActiveState.YES, false, null);

        assertThat(missingNames(MONTH)).contains("Aylık Hiç Yok");
    }

    /** MONTHLY + NO → kontrol kümesi dışı (hariç). */
    @Test
    void monthlyInactiveIsExcluded() {
        service("Aylık Pasif", Frequency.MONTHLY, ActiveState.NO, false, null);

        assertThat(missingNames(MONTH)).doesNotContain("Aylık Pasif");
    }

    /** informational=true (Multinet/sigorta) → hariç (Aylık + YES olsa bile). */
    @Test
    void monthlyInformationalIsExcluded() {
        service("Multinet Bilgi", Frequency.MONTHLY, ActiveState.YES, true, null);

        assertThat(missingNames(MONTH)).doesNotContain("Multinet Bilgi");
    }

    /**
     * Bir servisin SADECE bilgi-amaçlı ({@code informational=true}) bir expense'i FOUND
     * invoice taşısa bile o servis eksik listesinden DÜŞMEZ: found-set sorgusu
     * informational expense'leri hariç tutar (aksi halde dashboard missingCount çarpılır).
     */
    @Test
    void informationalFoundExpenseDoesNotClearServiceFromMissing() {
        com.ecommint.accounthr.domain.Service s =
                service("Bilgi-Found Eksik", Frequency.MONTHLY, ActiveState.YES, false, null);
        // Operasyonel (informational=false) FOUND invoice YOK; yalnızca informational FOUND var.
        expenseWithInvoice(s, InvoiceStatus.FOUND, true);

        // Hâlâ eksik olmalı (informational FOUND onu temizleyemez).
        assertThat(missingNames(MONTH)).contains("Bilgi-Found Eksik");
    }

    /**
     * E3 deep-review #4 — {@code lastSeenMonth} yalnızca OPERASYONEL ({@code
     * informational=false}) harcamaları yansıtır. Bir servisin bir aydaki TEK harcaması
     * bilgi-amaçlıysa o ay "en son görülen" sayılmaz.
     *
     * <p>Senaryo: servis bu ay (2026-09) eksik; operasyonel harcaması 2026-08'de, bilgi-amaçlı
     * harcaması DAHA SONRA 2026-11'de. lastSeen = 2026-08 (operasyonel) olmalı — 2026-11 DEĞİL.
     */
    @Test
    void lastSeenMonthIgnoresInformationalExpenses() {
        com.ecommint.accounthr.domain.Service s =
                service("LastSeen Bilgi", Frequency.MONTHLY, ActiveState.YES, false, null);

        Period aug = new Period();
        aug.setYear(2026);
        aug.setMonth(8);
        aug.setCode("2026-08");
        periodRepository.save(aug);

        Period nov = new Period();
        nov.setYear(2026);
        nov.setMonth(11);
        nov.setCode("2026-11");
        periodRepository.save(nov);

        // Operasyonel harcama 2026-08 (FOUND değil → bu ayı temizlemez, ama lastSeen kaynağı).
        Expense opAug = new Expense();
        opAug.setService(s);
        opAug.setPeriod(aug);
        opAug.setCurrency(Currency.TRY);
        opAug.setInformational(false);
        expenseRepository.save(opAug);

        // Bilgi-amaçlı harcama 2026-11 (daha büyük period kodu) — lastSeen'i KİRLETMEMELİ.
        Expense infoNov = new Expense();
        infoNov.setService(s);
        infoNov.setPeriod(nov);
        infoNov.setCurrency(Currency.TRY);
        infoNov.setInformational(true);
        expenseRepository.save(infoNov);

        Map<String, Object> row = getMissing(MONTH).stream()
                .filter(r -> "LastSeen Bilgi".equals(r.get("serviceName")))
                .findFirst().orElseThrow();
        assertThat(row.get("lastSeenMonth")).isEqualTo("2026-08");
    }

    /** YEARLY + YES, Aktif Aylar bu ayı İÇERİYOR, fatura yok → eksik. */
    @Test
    void yearlyActiveInRenewalMonthWithNoInvoiceIsMissing() {
        service("Yıllık Beklenen", Frequency.YEARLY, ActiveState.YES, false,
                "2026-03, 2026-09, 2026-12");

        assertThat(missingNames(MONTH)).contains("Yıllık Beklenen");
    }

    /** YEARLY + YES, Aktif Aylar bu ayı İÇERMİYOR → hariç (her ay eksik sayılmaz). */
    @Test
    void yearlyActiveOutsideRenewalMonthIsExcluded() {
        service("Yıllık Başka Ay", Frequency.YEARLY, ActiveState.YES, false,
                "2026-01, 2026-06");

        assertThat(missingNames(MONTH)).doesNotContain("Yıllık Başka Ay");
    }

    /** USAGE_BASED → kontrol kümesi dışı (hariç). */
    @Test
    void usageBasedIsExcluded() {
        service("Kullanım Bazlı", Frequency.USAGE_BASED, ActiveState.YES, false, null);

        assertThat(missingNames(MONTH)).doesNotContain("Kullanım Bazlı");
    }

    /** AD_HOC → kontrol kümesi dışı (hariç). */
    @Test
    void adHocIsExcluded() {
        service("Ad-hoc", Frequency.AD_HOC, ActiveState.YES, false, null);

        assertThat(missingNames(MONTH)).doesNotContain("Ad-hoc");
    }

    /** Eksik satır alanları (sağlayıcı, kart son-4, birincil e-posta, frekans) doğru eşlenir. */
    @Test
    void missingRowCarriesServiceMetadata() {
        com.ecommint.accounthr.domain.Service s =
                service("Aylık Meta", Frequency.MONTHLY, ActiveState.YES, false, null);
        contact(s, "ikincil@e-commint.com", false);
        contact(s, "birincil@e-commint.com", true);

        Map<String, Object> row = getMissing(MONTH).stream()
                .filter(r -> "Aylık Meta".equals(r.get("serviceName")))
                .findFirst().orElseThrow();

        assertThat(row.get("providerName")).isEqualTo("MissingTest Provider");
        assertThat(row.get("cardLast4")).isEqualTo("3800");
        assertThat(row.get("frequency")).isEqualTo("MONTHLY");
        assertThat(row.get("contactEmail")).isEqualTo("birincil@e-commint.com");
    }

    /** Bilinmeyen ama iyi-biçimli ay → boş liste (hata değil). */
    @Test
    void unknownMonthReturnsEmptyList() {
        service("Aylık X", Frequency.MONTHLY, ActiveState.YES, false, null);

        assertThat(getMissing("2099-09")).isEmpty();
    }

    /** Biçimsiz month → 400 INVALID_MONTH. */
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void malformedMonthReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken());
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/missing-invoices?month=garbage",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("INVALID_MONTH");
    }

    /** Kimlik doğrulamasız → 401. */
    @Test
    void unauthenticatedReturns401() {
        ResponseEntity<String> resp = rest.getForEntity(
                "/api/v1/missing-invoices?month=" + MONTH, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** DOD: eksik sayısı dashboard {@code missingCount} ile birebir aynı. */
    @Test
    void dashboardMissingCountMatchesMissingInvoicesSize() {
        // 2 eksik (biri Aylık-bekleniyor, biri Yıllık-beklenen ay), 1 bulundu, 1 hariç (kullanım).
        com.ecommint.accounthr.domain.Service a =
                service("Aylık Eksik", Frequency.MONTHLY, ActiveState.YES, false, null);
        expenseWithInvoice(a, InvoiceStatus.EXPECTED);
        service("Yıllık Eksik", Frequency.YEARLY, ActiveState.YES, false, "2026-09");
        com.ecommint.accounthr.domain.Service ok =
                service("Aylık Tamam", Frequency.MONTHLY, ActiveState.YES, false, null);
        expenseWithInvoice(ok, InvoiceStatus.FOUND);
        service("Kullanım", Frequency.USAGE_BASED, ActiveState.YES, false, null);

        int missingSize = getMissing(MONTH).size();
        long dashboardMissing = ((Number) dashboardSummary(MONTH).get("missingCount")).longValue();

        assertThat(missingSize).isEqualTo(2);
        assertThat(dashboardMissing).isEqualTo((long) missingSize);
    }

    // --- E3-10: approxTotalTry -------------------------------------------------

    /**
     * E3-10 — Yanıt zarfı {count, approxTotalTry} taşır. İki eksik servisin yaklaşık TL
     * tutarları toplanır; yaklaşık tutarı null olan eksik servis count'a girer ama toplama
     * 0 ekler.
     */
    @Test
    void missingResponseCarriesApproxTotalTry() {
        // Eksik #1: 1000.00, Eksik #2: 250.50, Eksik #3: null approx (count'a girer, toplama 0).
        serviceWithApprox("Eksik A", Frequency.MONTHLY, ActiveState.YES, false, null,
                new BigDecimal("1000.00"));
        serviceWithApprox("Eksik B", Frequency.MONTHLY, ActiveState.YES, false, null,
                new BigDecimal("250.50"));
        serviceWithApprox("Eksik Null", Frequency.MONTHLY, ActiveState.YES, false, null, null);
        // Bulundu (eksik değil) → toplama dahil DEĞİL, yüksek tutarlı olsa bile.
        com.ecommint.accounthr.domain.Service ok = serviceWithApprox(
                "Bulundu Yüksek", Frequency.MONTHLY, ActiveState.YES, false, null,
                new BigDecimal("99999.00"));
        expenseWithInvoice(ok, InvoiceStatus.FOUND);

        Map<String, Object> body = getMissingResponse(MONTH);

        assertThat(((Number) body.get("count")).intValue()).isEqualTo(3);
        assertThat(new BigDecimal(body.get("approxTotalTry").toString()))
                .isEqualByComparingTo(new BigDecimal("1250.50"));
    }

    /** Hiç eksik yoksa zarf boş items, count=0, approxTotalTry=0 döner. */
    @Test
    void missingResponseWithNoMissingHasZeroTotal() {
        com.ecommint.accounthr.domain.Service ok =
                service("Aylık Tamam", Frequency.MONTHLY, ActiveState.YES, false, null);
        expenseWithInvoice(ok, InvoiceStatus.FOUND);

        Map<String, Object> body = getMissingResponse(MONTH);

        assertThat(((Number) body.get("count")).intValue()).isEqualTo(0);
        assertThat(new BigDecimal(body.get("approxTotalTry").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * E3-10 — DOD tutarlılık: dashboard {@code missingTotalTry} eksik fatura ucunun
     * {@code approxTotalTry}'siyle birebir aynı (AYNI satır kümesi). null-approx satır her
     * iki tarafta da count'a girer, toplama 0 ekler.
     */
    @Test
    void dashboardMissingTotalMatchesMissingInvoicesTotal() {
        serviceWithApprox("Eksik 1", Frequency.MONTHLY, ActiveState.YES, false, null,
                new BigDecimal("1500.00"));
        serviceWithApprox("Eksik 2", Frequency.YEARLY, ActiveState.YES, false, "2026-09",
                new BigDecimal("3000.25"));
        serviceWithApprox("Eksik 3 Null", Frequency.MONTHLY, ActiveState.YES, false, null, null);

        BigDecimal missingTotal = new BigDecimal(
                getMissingResponse(MONTH).get("approxTotalTry").toString());
        BigDecimal dashboardTotal = new BigDecimal(
                dashboardSummary(MONTH).get("missingTotalTry").toString());

        // 1500.00 + 3000.25 + 0 (null) = 4500.25.
        assertThat(missingTotal).isEqualByComparingTo(new BigDecimal("4500.25"));
        assertThat(dashboardTotal).isEqualByComparingTo(missingTotal);
        // count tutarlılığı (3 eksik servis, biri null-approx).
        assertThat(((Number) getMissingResponse(MONTH).get("count")).intValue()).isEqualTo(3);
        assertThat(((Number) dashboardSummary(MONTH).get("missingCount")).longValue())
                .isEqualTo(3L);
    }
}
