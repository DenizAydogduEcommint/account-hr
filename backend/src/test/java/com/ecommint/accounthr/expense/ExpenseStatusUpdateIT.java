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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.AuditLog;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.AuditAction;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.AuditLogRepository;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E3-07 — {@code PATCH /api/v1/expenses/{id}/status} elle fatura durumu değiştirme
 * integration test.
 *
 * <p>HTTP üzerinden (TestRestTemplate + JWT, {@link ExpenseCreateIT} deseni) tam controller
 * + security + servis + audit zincirini doğrular: durum→renk türetimi (StatusColors tek
 * kaynak), 5 durumun belgeli hex eşlemesi, 404/400 davranışı, audit_log STATUS_CHANGE
 * satırı (kim + eski→yeni) ve operasyonel toplamın saf durum değişiminden etkilenmemesi.
 * {@link AbstractDataCleanupIT} her test öncesi/sonrası truncate eder → her surefire
 * sırasında geçer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExpenseStatusUpdateIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final String MONTH = "2026-08";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private Long expenseId;

    @BeforeEach
    void seed() {
        AppUser admin = new AppUser();
        adminEmail = "exp.status.admin@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Durum Değiştiren");
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

        com.ecommint.accounthr.domain.Service claude = new com.ecommint.accounthr.domain.Service();
        claude.setName("Claude AI");
        claude.setProvider(anthropic);
        claude.setDefaultCard(card3800);
        claude.setFrequency(Frequency.MONTHLY);
        claude.setActiveState(ActiveState.YES);
        claude.setInformational(false);
        serviceRepository.save(claude);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(8);
        period.setCode(MONTH);
        periodRepository.save(period);

        Expense expense = new Expense();
        expense.setService(claude);
        expense.setPeriod(period);
        expense.setCard(card3800);
        expense.setTransactionDate(LocalDate.of(2026, 8, 15));
        expense.setAmount(new BigDecimal("20.00"));
        expense.setCurrency(Currency.USD);
        expense.setAmountTry(new BigDecimal("680.50"));
        expense.setInformational(false);
        expenseRepository.save(expense);
        expenseId = expense.getId();

        // Tek (temsilci) invoice, başlangıç durumu EXPECTED (Bekleniyor).
        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(anthropic);
        invoice.setStatus(InvoiceStatus.EXPECTED);
        invoice.setAmount(new BigDecimal("20.00"));
        invoice.setCurrency(Currency.USD);
        invoice.setRefund(false);
        invoiceRepository.save(invoice);
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
    private ResponseEntity<Map> patch(Long id, Map<String, Object> body) {
        return rest.exchange("/api/v1/expenses/" + id + "/status", HttpMethod.PATCH,
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

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void patchChangesRepresentativeStatusAndDerivesColorAndReflectsInGet() {
        ResponseEntity<Map> resp = patch(expenseId, Map.of("status", "FOUND"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> row = resp.getBody();
        assertThat(row).isNotNull();
        assertThat(row.get("id")).isNotNull();
        assertThat(row.get("invoiceStatus")).isEqualTo("FOUND");
        // Renk durumdan türetilir (tek kaynak StatusColors), istekten DEĞİL.
        assertThat(row.get("invoiceColorHex")).isEqualTo("4CAF50");

        // GET listesindeki satır da yeni durumu + rengi gösterir.
        List<Map<String, Object>> content = mainContent(getList("?month=" + MONTH));
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("invoiceStatus")).isEqualTo("FOUND");
        assertThat(content.get(0).get("invoiceColorHex")).isEqualTo("4CAF50");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void eachStatusMapsToItsDocumentedHex() {
        // 5 durum → belgeli hex (CLAUDE.md / StatusColors tek kaynağı).
        Map<String, String> expected = Map.of(
                "FOUND", "4CAF50",
                "E_INVOICE", "8BC34A",
                "EXPECTED", "FF4444",
                "TO_INVESTIGATE", "FF9800",
                "IGNORED", "FF9800");
        for (Map.Entry<String, String> e : expected.entrySet()) {
            ResponseEntity<Map> resp = patch(expenseId, Map.of("status", e.getKey()));
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().get("invoiceStatus")).isEqualTo(e.getKey());
            assertThat(resp.getBody().get("invoiceColorHex"))
                    .as("hex for status %s", e.getKey())
                    .isEqualTo(e.getValue());
        }
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void unknownExpenseIdReturns404() {
        ResponseEntity<Map> resp = patch(999999L, Map.of("status", "FOUND"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void expenseWithoutAnyInvoiceReturns404() {
        // Veri-bütünlüğü değişmezi: her expense'in ≥1 invoice'u olmalı. Invoice'suz bir
        // expense BEKLENMEZ; updateStatus bunu sessizce iyileştirmez (yeni invoice
        // OLUŞTURMAZ) → fail-fast 404 ile yüzeye çıkar.
        Period period = periodRepository.findByCode(MONTH).orElseThrow();
        com.ecommint.accounthr.domain.Service claude = serviceRepository.findAll().get(0);
        Card card3800 = cardRepository.findByLastFour("3800").orElseThrow();

        Expense orphan = new Expense();
        orphan.setService(claude);
        orphan.setPeriod(period);
        orphan.setCard(card3800);
        orphan.setTransactionDate(LocalDate.of(2026, 8, 16));
        orphan.setAmount(new BigDecimal("10.00"));
        orphan.setCurrency(Currency.USD);
        orphan.setAmountTry(new BigDecimal("340.25"));
        orphan.setInformational(false);
        expenseRepository.save(orphan);
        // Bilinçli olarak HİÇBİR invoice kaydedilmedi.

        ResponseEntity<Map> resp = patch(orphan.getId(), Map.of("status", "FOUND"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");

        // Hiçbir invoice OLUŞTURULMADI (sessiz iyileştirme yok).
        assertThat(invoiceRepository.findFirstByExpenseIdOrderByIdDesc(orphan.getId()))
                .isEmpty();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void missingStatusReturns400() {
        ResponseEntity<Map> resp = patch(expenseId, Map.of()); // status yok
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void invalidStatusValueReturns400() {
        ResponseEntity<Map> resp = patch(expenseId, Map.of("status", "GARBAGE"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void unauthenticatedReturns401() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> resp = rest.exchange(
                "/api/v1/expenses/" + expenseId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "FOUND")), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void statusChangeLeavesAuditRowWithWhoAndOldToNew() {
        // Seed CREATE audit'lerini izole et ki yalnızca PATCH'in STATUS_CHANGE'ini görelim.
        auditLogRepository.deleteAll();

        ResponseEntity<Map> resp = patch(expenseId, Map.of("status", "FOUND"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<AuditLog> statusRows = auditLogRepository.findAll().stream()
                .filter(r -> r.getAction() == AuditAction.STATUS_CHANGE)
                .toList();
        assertThat(statusRows).hasSize(1);
        AuditLog audit = statusRows.get(0);
        assertThat(audit.getEntityType()).isEqualTo("Invoice");
        assertThat(audit.getFieldName()).isEqualTo("status");
        assertThat(audit.getOldValue()).isEqualTo("EXPECTED");
        assertThat(audit.getNewValue()).isEqualTo("FOUND");
        assertThat(audit.getChangedAt()).isNotNull();
        // changed_by = kimliği doğrulanmış kullanıcı (SecurityContext). changedBy LAZY proxy
        // olduğundan id ile karşılaştır (e-postaya gitmek session-dışı LazyInit hatası verir).
        assertThat(audit.getChangedBy()).isNotNull();
        Long expectedUserId = userRepository.findByEmail(adminEmail).orElseThrow().getId();
        assertThat(audit.getChangedBy().getId()).isEqualTo(expectedUserId);
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void pureStatusChangeDoesNotAffectOperationalTotal() {
        // Önce: operasyonel toplam = 680.50 (informational=false).
        Map<String, Object> before = getList("?month=" + MONTH);
        assertThat(new BigDecimal(before.get("operationalTotalTry").toString()))
                .isEqualByComparingTo(new BigDecimal("680.50"));

        // IGNORED'a geçir — saf durum değişimi; informational bayrağı DEĞİŞMEZ.
        assertThat(patch(expenseId, Map.of("status", "IGNORED")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> after = getList("?month=" + MONTH);
        // Operasyonel toplam DEĞİŞMEDİ (toplam informational'a bağlı, invoice durumuna değil).
        assertThat(new BigDecimal(after.get("operationalTotalTry").toString()))
                .isEqualByComparingTo(new BigDecimal("680.50"));
        // Satır hâlâ ANA listede (informational=false korundu) ve durumu IGNORED.
        List<Map<String, Object>> content = mainContent(after);
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("invoiceStatus")).isEqualTo("IGNORED");
        // Bilgi-amaçlı liste boş (bayrak değişmedi).
        assertThat(((List<?>) after.get("informationalRows"))).isEmpty();
    }
}
