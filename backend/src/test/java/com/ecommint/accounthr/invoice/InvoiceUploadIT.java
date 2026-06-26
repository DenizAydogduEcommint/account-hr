package com.ecommint.accounthr.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
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
import com.ecommint.accounthr.domain.Card;
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
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E3-05 — {@code POST /api/v1/invoices} fatura yükleme integration test.
 *
 * <p>HTTP üzerinden (TestRestTemplate + JWT, multipart) tam controller + security + servis
 * + dosya depolama zincirini doğrular. Storage kökü ASLA gerçek STORAGE_ROOT /
 * expenses/faturalar değil — {@link TempDir} + {@link DynamicPropertySource} ile izole bir
 * geçici dizine bağlanır. {@link AbstractDataCleanupIT} before/after truncate ile herhangi
 * bir test sırasında izolasyon sağlar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InvoiceUploadIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final String MONTH = "2026-03";

    /** Tüm test sınıfı için tek temp dizin; storage kökü buraya bağlanır. */
    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userEmail;
    private Provider provider;
    private Card card;

    @BeforeEach
    void seedBase() {
        AppUser user = new AppUser();
        userEmail = "upload.user@e-commint.com";
        user.setEmail(userEmail);
        user.setFullName("Yükleyen Üye");
        user.setRole(UserRole.TEAM_MEMBER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);

        provider = new Provider();
        provider.setName("Upload Provider");
        providerRepository.save(provider);

        card = new Card();
        card.setBank("Akbank");
        card.setLastFour("3800");
        cardRepository.save(card);
    }

    // --- seed helpers -------------------------------------------------------

    private com.ecommint.accounthr.domain.Service service(String name) {
        com.ecommint.accounthr.domain.Service s = new com.ecommint.accounthr.domain.Service();
        s.setName(name);
        s.setProvider(provider);
        s.setDefaultCard(card);
        s.setFrequency(Frequency.MONTHLY);
        s.setActiveState(ActiveState.YES);
        s.setInformational(false);
        s.setApproxAmountTry(new BigDecimal("100.00"));
        return serviceRepository.save(s);
    }

    private Period period(String code, int year, int month) {
        Period p = new Period();
        p.setYear(year);
        p.setMonth(month);
        p.setCode(code);
        return periodRepository.save(p);
    }

    /** EXPECTED durumunda invoice'lu bir harcama (Bekleniyor satırı) ekler. */
    private Expense expectedExpense(com.ecommint.accounthr.domain.Service s, Period period) {
        return expectedExpense(s, period, null);
    }

    /** EXPECTED durumunda invoice'lu (opsiyonel önceden-yazılmış notlu) bir harcama ekler. */
    private Expense expectedExpense(com.ecommint.accounthr.domain.Service s, Period period,
            String preexistingNote) {
        Expense e = new Expense();
        e.setService(s);
        e.setPeriod(period);
        e.setCurrency(Currency.TRY);
        e.setInformational(false);
        expenseRepository.save(e);

        Invoice inv = new Invoice();
        inv.setExpense(e);
        inv.setStatus(InvoiceStatus.EXPECTED);
        inv.setNote(preexistingNote);
        invoiceRepository.save(inv);
        return e;
    }

    /** Verilen byte içeriğiyle (her dosya AYNI içerik) bir multipart upload yapar (FIX 3). */
    private ResponseEntity<Map<String, Object>> uploadIdenticalContent(Long serviceId, String month,
            List<String> fileNames, byte[] sharedContent) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("serviceId", serviceId);
        body.add("month", month);
        for (String name : fileNames) {
            ByteArrayResource res = new ByteArrayResource(sharedContent) {
                @Override
                public String getFilename() {
                    return name;
                }
            };
            body.add("files", res);
        }
        return rest.exchange("/api/v1/invoices", HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    // --- HTTP helpers -------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", userEmail, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders multipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    /** İçinde verilen dosya adlarıyla bir multipart upload yapar. */
    private ResponseEntity<Map<String, Object>> upload(Long serviceId, String month,
            BigDecimal amount, Currency currency, String description, Boolean eInvoice,
            List<String> fileNames) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (serviceId != null) {
            body.add("serviceId", serviceId);
        }
        if (month != null) {
            body.add("month", month);
        }
        if (amount != null) {
            body.add("amount", amount.toPlainString());
        }
        if (currency != null) {
            body.add("currency", currency.name());
        }
        if (description != null) {
            body.add("description", description);
        }
        if (eInvoice != null) {
            body.add("eInvoice", eInvoice.toString());
        }
        for (String name : fileNames) {
            // Her dosyaya benzersiz içerik ver ki SHA-256 duplicate'e takılmasın.
            byte[] content = ("PDF-CONTENT-" + name + "-" + System.nanoTime()).getBytes();
            ByteArrayResource res = new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return name;
                }
            };
            body.add("files", res);
        }
        return rest.exchange("/api/v1/invoices", HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders()),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    private List<String> missingNames(String month) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token());
        ResponseEntity<List<Map<String, Object>>> resp = rest.exchange(
                "/api/v1/missing-invoices?month=" + month, HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().stream().map(r -> (String) r.get("serviceName")).toList();
    }

    // --- tests --------------------------------------------------------------

    /** Var olan EXPECTED harcamaya yükleme → FOUND olur, FileAsset bağlanır, dosya diske yazılır. */
    @Test
    void uploadToExistingExpectedExpenseBecomesFound() throws Exception {
        com.ecommint.accounthr.domain.Service s = service("Google Workspace");
        Period p = period(MONTH, 2026, 3);
        Expense expense = expectedExpense(s, p);

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, new BigDecimal("123.45"), Currency.TRY,
                "Mart faturası", false, List.of("gw_mart.pdf"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("status")).isEqualTo("FOUND");
        assertThat(body.get("expenseCreated")).isEqualTo(false);
        assertThat(((Number) body.get("expenseId")).longValue()).isEqualTo(expense.getId());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) body.get("files");
        assertThat(files).hasSize(1);
        String filePath = (String) files.get(0).get("filePath");
        assertThat(filePath).startsWith("2026-03/");

        // FileAsset DB'de invoice'a bağlı.
        Long invoiceId = ((Number) body.get("invoiceId")).longValue();
        assertThat(fileAssetRepository.findByInvoiceId(invoiceId)).hasSize(1);

        // Fiziksel dosya temp storage kökü altında var.
        assertThat(Files.exists(storageRoot.resolve(filePath))).isTrue();

        // Var olan invoice güncellendi (yeni eklenmedi): expense'in tek invoice'u FOUND.
        List<Invoice> invoices = invoiceRepository.findByExpenseId(expense.getId());
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getStatus()).isEqualTo(InvoiceStatus.FOUND);
    }

    /** Harcaması olmayan servis+ay → expense + invoice (FOUND) oluşturulur. */
    @Test
    void uploadForServiceWithoutExpenseCreatesExpenseAndInvoice() {
        com.ecommint.accounthr.domain.Service s = service("Contabo VPS");
        // period yok → upload da oluşturmalı.

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, new BigDecimal("30.00"), Currency.EUR,
                null, false, List.of("contabo_mart.pdf"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = resp.getBody();
        assertThat(body.get("status")).isEqualTo("FOUND");
        assertThat(body.get("expenseCreated")).isEqualTo(true);

        // Period ve expense gerçekten oluştu.
        assertThat(periodRepository.findByCode(MONTH)).isPresent();
        Long expenseId = ((Number) body.get("expenseId")).longValue();
        assertThat(expenseRepository.findById(expenseId)).isPresent();
    }

    /** eInvoice=true → durum E_INVOICE. */
    @Test
    void uploadWithEInvoiceFlagBecomesEInvoice() {
        com.ecommint.accounthr.domain.Service s = service("LeasePlan");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, null, null, null, true, List.of("leaseplan_mart.xml"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("status")).isEqualTo("E_INVOICE");
    }

    /** Birden çok dosya tek yüklemede → hepsi bağlanır (_1/_2 isimlendirme). */
    @Test
    void uploadMultipleFilesLinksAll() {
        com.ecommint.accounthr.domain.Service s = service("AWS");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, null, null, null, false,
                List.of("aws_mart.pdf", "aws_mart_statement.pdf"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) resp.getBody().get("files");
        assertThat(files).hasSize(2);
        Long invoiceId = ((Number) resp.getBody().get("invoiceId")).longValue();
        assertThat(fileAssetRepository.findByInvoiceId(invoiceId)).hasSize(2);
    }

    /** İzin verilmeyen dosya tipi → 400, hiçbir şey kaydedilmez. */
    @Test
    void uploadWithDisallowedFileTypeReturns400() {
        com.ecommint.accounthr.domain.Service s = service("Bad Type");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, null, null, null, false, List.of("malware.exe"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("STORAGE_ERROR");
        // Hiç expense/invoice/dosya oluşmadı.
        assertThat(expenseRepository.findAll()).isEmpty();
        assertThat(fileAssetRepository.findAll()).isEmpty();
    }

    /** Bilinmeyen servis → 404. */
    @Test
    void uploadWithUnknownServiceReturns404() {
        ResponseEntity<Map<String, Object>> resp = upload(
                999999L, MONTH, null, null, null, false, List.of("x_mart.pdf"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((String) resp.getBody().get("error")).isEqualTo("NOT_FOUND");
    }

    /** Biçimsiz month → 400. */
    @Test
    void uploadWithMalformedMonthReturns400() {
        com.ecommint.accounthr.domain.Service s = service("Month Test");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), "garbage", null, null, null, false, List.of("x_mart.pdf"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Kimlik doğrulamasız → 401. */
    @Test
    void unauthenticatedReturns401() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("serviceId", 1L);
        body.add("month", MONTH);
        ByteArrayResource res = new ByteArrayResource("x".getBytes()) {
            @Override
            public String getFilename() {
                return "x_mart.pdf";
            }
        };
        body.add("files", res);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> resp = rest.exchange("/api/v1/invoices", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * DOD — yükleme sonrası servis ARTIK eksik listesinde DEĞİL (eksik sayısı düşer).
     * Önce eksik (FOUND harcaması yok), yüklemeden sonra eksik değil.
     */
    @Test
    void afterUploadServiceNoLongerMissing() {
        com.ecommint.accounthr.domain.Service s = service("HepsiBurada");
        Period p = period(MONTH, 2026, 3);
        expectedExpense(s, p); // Bekleniyor → şu an EKSİK.

        assertThat(missingNames(MONTH)).contains("HepsiBurada");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, null, null, null, false, List.of("hb_mart.pdf"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Artık eksik değil.
        assertThat(missingNames(MONTH)).doesNotContain("HepsiBurada");
    }

    /** İade/refund tutarsızlığını önlemek için: transactionDate set edilmeyen yeni expense sorun çıkarmaz. */
    @Test
    void uploadCreatesExpenseWithDefaultCardFromService() {
        com.ecommint.accounthr.domain.Service s = service("Card Default");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, new BigDecimal("50.00"), Currency.TRY, null, false,
                List.of("cd_mart.pdf"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long expenseId = ((Number) resp.getBody().get("expenseId")).longValue();
        Expense e = expenseRepository.findById(expenseId).orElseThrow();
        assertThat(e.getCard()).isNotNull();
        // Card lazy proxy: id'yi (FK) servisin varsayılan kartıyla karşılaştır (init tetiklemez).
        assertThat(e.getCard().getId()).isEqualTo(card.getId());
    }

    /**
     * E3 deep-review #1 (Stored-XSS) — istemcinin gönderdiği Content-Type ne olursa olsun,
     * depolanan mimeType UZANTIDAN türetilir. {@code .pdf} uzantılı dosya → {@code
     * application/pdf} (asla {@code text/html} değil). Böylece önizleme inline HTML render
     * edemez.
     */
    @Test
    void uploadDerivesMimeTypeFromExtensionNotClientContentType() {
        com.ecommint.accounthr.domain.Service s = service("XSS Mime");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, null, null, null, false, List.of("x.pdf"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long invoiceId = ((Number) resp.getBody().get("invoiceId")).longValue();
        List<com.ecommint.accounthr.domain.FileAsset> assets =
                fileAssetRepository.findByInvoiceId(invoiceId);
        assertThat(assets).hasSize(1);
        // SUNUCU TARAFI türetme: .pdf → application/pdf (istemci Content-Type yok sayıldı).
        assertThat(assets.get(0).getMimeType()).isEqualTo("application/pdf");
    }

    /**
     * E3 deep-review #3 — Upload ile YENİ oluşturulan expense: {@code source=MANUAL}
     * (ekstreden gelmedi) ve {@code transactionDate} = ayın 1'i (null değil; E3-06
     * "tarih zorunlu" sözleşmesiyle tutarlı).
     */
    @Test
    void uploadCreatedExpenseHasManualSourceAndFirstOfMonthDate() {
        com.ecommint.accounthr.domain.Service s = service("Source Date");

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, new BigDecimal("42.00"), Currency.TRY, null, false,
                List.of("sd_mart.pdf"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("expenseCreated")).isEqualTo(true);

        Long expenseId = ((Number) resp.getBody().get("expenseId")).longValue();
        Expense e = expenseRepository.findById(expenseId).orElseThrow();
        assertThat(e.getSource()).isEqualTo(
                com.ecommint.accounthr.domain.enums.ExpenseSource.MANUAL);
        assertThat(e.getTransactionDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 1));
    }

    /**
     * FIX 2 — Pre-existing invoice'un notu KÖRÜ KÖRÜNE EZİLMEZ; yeni bilgi eklenir.
     * Reconciliation/importer'ın yazdığı not (ör. dosya path'i) korunmalı.
     */
    @Test
    void uploadPreservesExistingInvoiceNote() {
        com.ecommint.accounthr.domain.Service s = service("Note Preserve");
        Period p = period(MONTH, 2026, 3);
        String oldNote = "RECON: faturalar/2026-03/eski_kayit.pdf";
        Expense expense = expectedExpense(s, p, oldNote);

        ResponseEntity<Map<String, Object>> resp = upload(
                s.getId(), MONTH, new BigDecimal("99.00"), Currency.TRY,
                "Mart faturası", false, List.of("np_mart.pdf"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Invoice updated = invoiceRepository.findByExpenseId(expense.getId()).get(0);
        // Eski not KORUNDU ve yeni bilgi EKLENDİ (ezilmedi).
        assertThat(updated.getNote()).contains(oldNote);
        assertThat(updated.getNote()).contains("np_mart.pdf");
        assertThat(updated.getNote()).isNotEqualTo(oldNote);
    }

    /**
     * FIX 4 (E3) — Zaten FOUND bir expense'e re-upload İKİNCİ bir invoice oluşturmaz;
     * mevcut (temsilci) invoice yeniden kullanılır. Önceki mantık yalnızca EXPECTED
     * invoice'u tekrar kullandığından FOUND bir expense'e tekrar yükleme orphan FOUND
     * invoice yaratıyordu (şişmiş sayım).
     */
    @Test
    void reuploadToAlreadyFoundExpenseDoesNotCreateSecondInvoice() {
        com.ecommint.accounthr.domain.Service s = service("Re-upload");
        Period p = period(MONTH, 2026, 3);

        // İlk yükleme: EXPECTED → FOUND.
        Expense expense = expectedExpense(s, p);
        ResponseEntity<Map<String, Object>> first = upload(
                s.getId(), MONTH, new BigDecimal("10.00"), Currency.TRY,
                null, false, List.of("ru_mart.pdf"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long firstInvoiceId = ((Number) first.getBody().get("invoiceId")).longValue();
        assertThat(invoiceRepository.findByExpenseId(expense.getId())).hasSize(1);

        // İkinci yükleme (re-upload) AYNI expense'e: yeni invoice YARATMAMALI.
        ResponseEntity<Map<String, Object>> second = upload(
                s.getId(), MONTH, new BigDecimal("20.00"), Currency.TRY,
                null, false, List.of("ru_mart_2.pdf"));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long secondInvoiceId = ((Number) second.getBody().get("invoiceId")).longValue();

        // Hâlâ TEK invoice (aynı id) ve durumu FOUND.
        List<Invoice> invoices = invoiceRepository.findByExpenseId(expense.getId());
        assertThat(invoices).hasSize(1);
        assertThat(secondInvoiceId).isEqualTo(firstInvoiceId);
        assertThat(invoices.get(0).getStatus()).isEqualTo(InvoiceStatus.FOUND);
    }

    /**
     * FIX 3 — Tek istekte AYNI içerikli iki dosya → TEK fiziksel dosya + TEK FileAsset,
     * 201 (orphan yok, 409 yok). Batch-içi SHA-256 dedup ikinci dosyayı yazmaz.
     */
    @Test
    void uploadTwoIdenticalFilesYieldsSinglePhysicalFileAndAsset() throws Exception {
        com.ecommint.accounthr.domain.Service s = service("Dup Content");
        byte[] shared = "IDENTICAL-PDF-BYTES-FOR-DEDUP".getBytes();

        ResponseEntity<Map<String, Object>> resp = uploadIdenticalContent(
                s.getId(), MONTH, List.of("dup_mart.pdf", "dup_mart_copy.pdf"), shared);

        // Temiz sonuç: 409 değil, 201.
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long invoiceId = ((Number) resp.getBody().get("invoiceId")).longValue();

        // TEK FileAsset (ikinci içerik-eşi yazılmadı).
        List<com.ecommint.accounthr.domain.FileAsset> assets =
                fileAssetRepository.findByInvoiceId(invoiceId);
        assertThat(assets).hasSize(1);

        // Diskte bu servise ait TEK fiziksel dosya: ikinci içerik-eşi için _1 üretilmedi.
        // (storageRoot @SpringBootTest boyunca paylaşılır → bu servisin slug'ına göre say.)
        try (var stream = Files.list(storageRoot.resolve("2026-03"))) {
            long dupFiles = stream
                    .filter(f -> f.getFileName().toString().startsWith("dup_content_"))
                    .count();
            assertThat(dupFiles).isEqualTo(1);
        }
        // FileAsset path'i de tam o tek dosyaya işaret etmeli.
        assertThat(assets.get(0).getFilePath()).isEqualTo("2026-03/dup_content_mart.pdf");
    }
}
