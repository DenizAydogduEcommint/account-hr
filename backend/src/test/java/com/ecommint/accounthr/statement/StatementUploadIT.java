package com.ecommint.accounthr.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.ecommint.accounthr.domain.RawTransaction;
import com.ecommint.accounthr.domain.enums.RawTxnStatus;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.RawTransactionRepository;
import com.ecommint.accounthr.service.statement.DefaultStatementParser;

import java.nio.file.Path;

/**
 * E4-01 — {@code /api/v1/statements} ekstre yükleme + parse pipeline integration test.
 *
 * <p>HTTP üzerinden (TestRestTemplate + JWT, multipart) tam controller + security + parse +
 * persist zincirini doğrular. Storage kökü {@link TempDir} + {@link DynamicPropertySource}
 * ile izole edilir. {@link AbstractDataCleanupIT} before/after truncate ile herhangi bir
 * surefire sırasında izolasyon sağlar.
 *
 * <p>Parser şu an placeholder: gerçek satır çıkarımı yok → boş işlem listesi + uyarı.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StatementUploadIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final String MONTH = "2026-04";
    private static final String CARD_LAST4 = "3800";

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private RawTransactionRepository rawTransactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String accountingEmail;
    private String teamMemberEmail;

    @BeforeEach
    void seed() {
        accountingEmail = "stmt.accounting@e-commint.com";
        AppUser accounting = new AppUser();
        accounting.setEmail(accountingEmail);
        accounting.setFullName("Muhasebe");
        accounting.setRole(UserRole.ACCOUNTING);
        accounting.setActive(true);
        accounting.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(accounting);

        teamMemberEmail = "stmt.member@e-commint.com";
        AppUser member = new AppUser();
        member.setEmail(teamMemberEmail);
        member.setFullName("Ekip Üyesi");
        member.setRole(UserRole.TEAM_MEMBER);
        member.setActive(true);
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(member);

        Card card = new Card();
        card.setBank("Akbank");
        card.setLastFour(CARD_LAST4);
        card.setHolderName("Kaan Bingöl");
        cardRepository.save(card);
    }

    // --- helpers ------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders multipartHeaders(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private HttpHeaders jsonHeaders(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Minimal geçerli .xlsx (POI ile oluşturulmuş tek hücreli workbook). */
    private static byte[] minimalXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Ekstre").createRow(0).createCell(0).setCellValue("Tarih");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private ResponseEntity<Map<String, Object>> upload(String email, String cardLast4, String month,
            String filename, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (cardLast4 != null) {
            body.add("cardLast4", cardLast4);
        }
        if (month != null) {
            body.add("month", month);
        }
        ByteArrayResource res = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("file", res);
        return rest.exchange("/api/v1/statements", HttpMethod.POST,
                new HttpEntity<>(body, multipartHeaders(email)),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    private ResponseEntity<Map<String, Object>> confirm(String email, String batchRef) {
        return rest.exchange("/api/v1/statements/confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("batchRef", batchRef), jsonHeaders(email)),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    private ResponseEntity<Map<String, Object>> getBatch(String email, String batchRef) {
        return rest.exchange("/api/v1/statements/" + batchRef, HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(email)),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    // --- tests --------------------------------------------------------------

    /** .xlsx yükleme → 200, boş işlem listesi + placeholder uyarı, sha256 ile PENDING satır(lar). */
    @Test
    void uploadXlsxReturnsPreviewWithPlaceholderWarning() throws Exception {
        byte[] xlsx = minimalXlsx();
        ResponseEntity<Map<String, Object>> resp = upload(
                accountingEmail, CARD_LAST4, MONTH, "akbank_nisan.xlsx", xlsx);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("card")).isEqualTo(CARD_LAST4);
        assertThat(body.get("month")).isEqualTo(MONTH);
        assertThat(body.get("alreadyUploaded")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<Object> txns = (List<Object>) body.get("transactions");
        assertThat(txns).isEmpty(); // placeholder: gerçek satır çıkarımı yok

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) body.get("warnings");
        assertThat(warnings).contains(DefaultStatementParser.PLACEHOLDER_WARNING);

        // batchRef = sha256 (64 hex).
        String batchRef = (String) body.get("batchRef");
        assertThat(batchRef).hasSize(64);

        // Placeholder boş liste döndüğünden persist edilen satır yok; sha256 yine batchRef olarak döner.
        assertThat(rawTransactionRepository.findBySourceFileSha256(batchRef)).isEmpty();
    }

    /**
     * Parser placeholder boş liste döndürdüğü için persist edilen satır olmaz. Idempotency +
     * confirm akışını gerçek satırlarla doğrulamak adına bir PENDING satırı doğrudan ekleyip
     * confirm sonrası aynı dosya+kart+dönem re-upload'ın alreadyUploaded=true döndüğünü test ederiz.
     */
    @Test
    void reuploadAfterConfirmIsIdempotent() throws Exception {
        byte[] xlsx = minimalXlsx();
        ResponseEntity<Map<String, Object>> first = upload(
                accountingEmail, CARD_LAST4, MONTH, "akbank_nisan.xlsx", xlsx);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        String batchRef = (String) first.getBody().get("batchRef");

        // Placeholder hiç satır üretmez → idempotency'yi gözlemleyebilmek için bir CONFIRMED
        // satırı (aynı sha256+kart+dönem) elle ekle (E4-02 gerçek satır akışını taklit).
        // Period upload sırasında zaten oluşturuldu → mevcut olanı kullan.
        Card card = cardRepository.findByLastFour(CARD_LAST4).orElseThrow();
        RawTransaction rt = new RawTransaction();
        rt.setCard(card);
        rt.setPeriod(periodRepositoryFindByCode());
        rt.setCurrency(com.ecommint.accounthr.domain.enums.Currency.TRY);
        rt.setStatus(RawTxnStatus.CONFIRMED);
        rt.setSourceFileSha256(batchRef);
        rt.setSourceFileName("akbank_nisan.xlsx");
        rt.setMatched(false);
        rawTransactionRepository.save(rt);

        long before = rawTransactionRepository.count();

        // Aynı dosya+kart+dönem re-upload → alreadyUploaded=true, yeni satır eklenmez.
        ResponseEntity<Map<String, Object>> second = upload(
                accountingEmail, CARD_LAST4, MONTH, "akbank_nisan.xlsx", xlsx);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("alreadyUploaded")).isEqualTo(true);
        assertThat(rawTransactionRepository.count()).isEqualTo(before);
    }

    @Autowired private com.ecommint.accounthr.repository.PeriodRepository periodRepository;

    private com.ecommint.accounthr.domain.Period periodRepositoryFindByCode() {
        return periodRepository.findByCode(MONTH).orElseThrow();
    }

    /** confirm bir batch'in PENDING satırlarını CONFIRMED yapar. */
    @Test
    void confirmFlipsPendingToConfirmed() throws Exception {
        // upload period'u oluşturur (placeholder satır üretmez).
        byte[] xlsx = minimalXlsx();
        ResponseEntity<Map<String, Object>> up = upload(
                accountingEmail, CARD_LAST4, MONTH, "akbank_nisan.xlsx", xlsx);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        String batchRef = (String) up.getBody().get("batchRef");

        // Bir PENDING satır elle ekle (gerçek parse satırını taklit).
        Card card = cardRepository.findByLastFour(CARD_LAST4).orElseThrow();
        RawTransaction rt = new RawTransaction();
        rt.setCard(card);
        rt.setPeriod(periodRepositoryFindByCode());
        rt.setCurrency(com.ecommint.accounthr.domain.enums.Currency.TRY);
        rt.setStatus(RawTxnStatus.PENDING);
        rt.setSourceFileSha256(batchRef);
        rt.setMatched(false);
        rawTransactionRepository.save(rt);

        ResponseEntity<Map<String, Object>> resp = confirm(accountingEmail, batchRef);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) resp.getBody().get("confirmed")).intValue()).isEqualTo(1);

        List<RawTransaction> rows = rawTransactionRepository.findBySourceFileSha256(batchRef);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(RawTxnStatus.CONFIRMED);
    }

    /** Bilinmeyen kart → 400 VALIDATION_ERROR. */
    @Test
    void unknownCardReturns400() throws Exception {
        ResponseEntity<Map<String, Object>> resp = upload(
                accountingEmail, "0000", MONTH, "x.xlsx", minimalXlsx());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    /** Biçimsiz month → 400 VALIDATION_ERROR. */
    @Test
    void badMonthReturns400() throws Exception {
        ResponseEntity<Map<String, Object>> resp = upload(
                accountingEmail, CARD_LAST4, "garbage", "x.xlsx", minimalXlsx());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    /** Ekip üyesi rolü → 403 (yükleme muhasebe/yönetici işidir). */
    @Test
    void teamMemberRoleReturns403() throws Exception {
        ResponseEntity<Map<String, Object>> resp = upload(
                teamMemberEmail, CARD_LAST4, MONTH, "x.xlsx", minimalXlsx());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** .pdf → desteklenmeyen format uyarısı (500 DEĞİL, 200). */
    @Test
    void pdfReturnsUnsupportedWarning() {
        byte[] fakePdf = "%PDF-1.4 fake".getBytes();
        ResponseEntity<Map<String, Object>> resp = upload(
                accountingEmail, CARD_LAST4, MONTH, "ekstre.pdf", fakePdf);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) resp.getBody().get("warnings");
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0)).contains("Desteklenmeyen dosya formatı");
        @SuppressWarnings("unchecked")
        List<Object> txns = (List<Object>) resp.getBody().get("transactions");
        assertThat(txns).isEmpty();
    }

    /** .doc → desteklenmeyen format uyarısı (Word için yalnızca .docx; 500 DEĞİL). */
    @Test
    void docReturnsUnsupportedWarning() {
        byte[] fakeDoc = "fake doc".getBytes();
        ResponseEntity<Map<String, Object>> resp = upload(
                accountingEmail, CARD_LAST4, MONTH, "ekstre.doc", fakeDoc);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) resp.getBody().get("warnings");
        assertThat(warnings).isNotEmpty();
        assertThat(warnings.get(0)).contains("Desteklenmeyen dosya formatı");
    }

    /**
     * Fix 2: rawText, GET batch yanıtındaki DTO'da round-trip eder. Bir RawTransaction'ı
     * rawText ile seed edip GET /{batchRef} ile çekeriz; transactions[0].rawText taşınmalı.
     */
    @Test
    void rawTextRoundTripsInBatchResponse() throws Exception {
        // upload period'u oluşturur (placeholder satır üretmez).
        byte[] xlsx = minimalXlsx();
        ResponseEntity<Map<String, Object>> up = upload(
                accountingEmail, CARD_LAST4, MONTH, "akbank_nisan.xlsx", xlsx);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        String batchRef = (String) up.getBody().get("batchRef");

        Card card = cardRepository.findByLastFour(CARD_LAST4).orElseThrow();
        RawTransaction rt = new RawTransaction();
        rt.setCard(card);
        rt.setPeriod(periodRepositoryFindByCode());
        rt.setCurrency(com.ecommint.accounthr.domain.enums.Currency.TRY);
        rt.setStatus(RawTxnStatus.PENDING);
        rt.setSourceFileSha256(batchRef);
        rt.setRawText("01.04.2026  CLAUDE.AI SUBSCRIPTION  100,00 TL");
        rt.setMatched(false);
        rawTransactionRepository.save(rt);

        ResponseEntity<Map<String, Object>> resp = getBatch(accountingEmail, batchRef);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txns = (List<Map<String, Object>>) resp.getBody().get("transactions");
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).get("rawText"))
                .isEqualTo("01.04.2026  CLAUDE.AI SUBSCRIPTION  100,00 TL");
    }

    /**
     * Fix 3: idempotency önizlemesi kart+dönem kapsamlıdır. Aynı sha256 iki farklı kart
     * altında CONFIRMED iken, (kart A, dönem) için re-upload yalnızca kart A'nın satırını
     * döndürmeli — çapraz-kart sızıntısı olmamalı.
     */
    @Test
    void idempotencyPreviewIsScopedToCardAndPeriod() throws Exception {
        byte[] xlsx = minimalXlsx();
        // (kart A=CARD_LAST4, dönem MONTH) upload → period oluşur, sha256 = batchRef.
        ResponseEntity<Map<String, Object>> up = upload(
                accountingEmail, CARD_LAST4, MONTH, "akbank_nisan.xlsx", xlsx);
        assertThat(up.getStatusCode()).isEqualTo(HttpStatus.OK);
        String batchRef = (String) up.getBody().get("batchRef");

        Card cardA = cardRepository.findByLastFour(CARD_LAST4).orElseThrow();
        // İkinci kart B (farklı last4).
        Card cardB = new Card();
        cardB.setBank("YKB");
        cardB.setLastFour("3909");
        cardB.setHolderName("Kaan Bingöl");
        cardRepository.save(cardB);

        com.ecommint.accounthr.domain.Period period = periodRepositoryFindByCode();

        // Kart A altında CONFIRMED satır (aynı sha256+dönem).
        RawTransaction rtA = new RawTransaction();
        rtA.setCard(cardA);
        rtA.setPeriod(period);
        rtA.setCurrency(com.ecommint.accounthr.domain.enums.Currency.TRY);
        rtA.setStatus(RawTxnStatus.CONFIRMED);
        rtA.setSourceFileSha256(batchRef);
        rtA.setRawText("KART A SATIR");
        rtA.setMatched(false);
        rawTransactionRepository.save(rtA);

        // Kart B altında aynı sha256 ile CONFIRMED satır (çapraz-kart sızıntı tuzağı).
        RawTransaction rtB = new RawTransaction();
        rtB.setCard(cardB);
        rtB.setPeriod(period);
        rtB.setCurrency(com.ecommint.accounthr.domain.enums.Currency.TRY);
        rtB.setStatus(RawTxnStatus.CONFIRMED);
        rtB.setSourceFileSha256(batchRef);
        rtB.setRawText("KART B SATIR");
        rtB.setMatched(false);
        rawTransactionRepository.save(rtB);

        // (kart A, dönem) için re-upload → alreadyUploaded=true, yalnızca kart A satırı.
        ResponseEntity<Map<String, Object>> reupload = upload(
                accountingEmail, CARD_LAST4, MONTH, "akbank_nisan.xlsx", xlsx);
        assertThat(reupload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reupload.getBody().get("alreadyUploaded")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> txns =
                (List<Map<String, Object>>) reupload.getBody().get("transactions");
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).get("rawText")).isEqualTo("KART A SATIR");
    }

    /** Kimlik doğrulamasız → 401. */
    @Test
    void unauthenticatedReturns401() throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("cardLast4", CARD_LAST4);
        body.add("month", MONTH);
        ByteArrayResource res = new ByteArrayResource(minimalXlsx()) {
            @Override
            public String getFilename() {
                return "x.xlsx";
            }
        };
        body.add("file", res);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> resp = rest.exchange("/api/v1/statements", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
