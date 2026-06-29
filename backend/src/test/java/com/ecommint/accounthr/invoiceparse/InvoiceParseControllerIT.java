package com.ecommint.accounthr.invoiceparse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;

/**
 * E5-03 — {@code POST /api/v1/invoices/parse} integration test.
 *
 * <p>HTTP üzerinden (TestRestTemplate + JWT, multipart) controller + security + parser
 * zincirini doğrular. Yüklenen PDF {@link SyntheticInvoicePdf} ile in-memory üretilir;
 * gerçek/gizli fatura ASLA kullanılmaz. Hiçbir dosya depolanmaz (uç persist etmez).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InvoiceParseControllerIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private String teamEmail;

    @BeforeEach
    void seedUsers() {
        adminEmail = "parse.admin@e-commint.com";
        saveUser(adminEmail, UserRole.ADMIN);
        teamEmail = "parse.member@e-commint.com";
        saveUser(teamEmail, UserRole.TEAM_MEMBER);
    }

    private void saveUser(String email, UserRole role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName(email);
        u.setRole(role);
        u.setActive(true);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(u);
    }

    @Test
    void adminParsesPdfAndGetsStructuredFields() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of(
                "Invoice",
                "Invoice number TEST-001",
                "Date of issue October 7, 2025",
                "OpenAI, LLC",
                "$36.00 USD due October 7, 2025",
                "Subtotal $30.00",
                "Total excluding tax $30.00",
                "VAT - Turkey (20% on $30.00) $6.00",
                "Total $36.00",
                "Amount due $36.00 USD"));

        ResponseEntity<Map<String, Object>> resp = parse(adminEmail, "openai.pdf", pdf,
                MediaType.APPLICATION_PDF);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("invoiceNumber")).isEqualTo("TEST-001");
        assertThat(body.get("issueDate")).isEqualTo("2025-10-07");
        assertThat(body.get("currency")).isEqualTo("USD");
        assertThat(body.get("providerName")).isEqualTo("OpenAI, LLC");
        // JSON BigDecimal serileştirmesi sondaki sıfırları atabilir (36.00 -> 36.0);
        // sayısal değeri karşılaştır.
        assertThat(new java.math.BigDecimal(body.get("totalAmount").toString()))
                .isEqualByComparingTo("36.00");
        assertThat(new java.math.BigDecimal(body.get("vatAmount").toString()))
                .isEqualByComparingTo("6.00");
        assertThat(new java.math.BigDecimal(body.get("vatRate").toString()))
                .isEqualByComparingTo("20");
        // rawText yanıtta sızdırılmamalı.
        assertThat(body).doesNotContainKey("rawText");
    }

    @Test
    void corruptPdfReturns200WithWarnings() {
        byte[] garbage = "not a pdf".getBytes();

        ResponseEntity<Map<String, Object>> resp = parse(adminEmail, "broken.pdf", garbage,
                MediaType.APPLICATION_PDF);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("invoiceNumber")).isNull();
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) body.get("warnings");
        assertThat(warnings).isNotEmpty();
    }

    @Test
    void nonPdfExtensionReturns400() {
        byte[] bytes = "hello".getBytes();

        ResponseEntity<Map<String, Object>> resp = parse(adminEmail, "statement.jpg", bytes,
                MediaType.IMAGE_JPEG);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void teamMemberForbidden() {
        byte[] pdf = SyntheticInvoicePdf.of(List.of("Invoice", "Invoice number X-1", "Total $1.00"));

        ResponseEntity<Map<String, Object>> resp = parse(teamEmail, "x.pdf", pdf,
                MediaType.APPLICATION_PDF);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> parse(String email, String filename, byte[] content,
            MediaType partType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource res = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(partType);
        body.add("file", new HttpEntity<>(res, partHeaders));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return rest.exchange("/api/v1/invoices/parse", HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }
}
