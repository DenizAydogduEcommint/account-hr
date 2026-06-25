package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * E1-07 standart hata formatı testi.
 *
 * <p>{@code POST /api/v1/auth/login} boş gövdeyle çağrılır → @Valid başarısız →
 * 400 VALIDATION_ERROR. Yanıtın standart {@code ErrorResponse} alanlarını ve
 * alan bazlı {@code fieldErrors} listesini, ayrıca {@code traceId} alanının
 * mevcut olduğunu (null olabilir) doğrular.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorFormatIT {

    @Autowired private TestRestTemplate rest;

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void validationFailureReturnsStandardErrorWithFieldErrors() {
        // Boş email + boş password → @NotBlank/@Email ihlali.
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", "", "password", ""), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();

        // Standart ErrorResponse alanları
        assertThat(body).containsKeys(
                "timestamp", "status", "error", "message", "path", "traceId", "fieldErrors");
        assertThat(((Number) body.get("status")).intValue()).isEqualTo(400);
        assertThat(body.get("error")).isEqualTo("VALIDATION_ERROR");
        assertThat((String) body.get("message")).isNotBlank();
        assertThat(body.get("path")).isEqualTo("/api/v1/auth/login");

        // CorrelationIdFilter her isteğe bir id atar → traceId dolu olmalı.
        assertThat(body.get("traceId")).isInstanceOf(String.class);
        assertThat((String) body.get("traceId")).isNotBlank();

        // Alan bazlı hata listesi
        List<Map<String, Object>> fieldErrors = (List<Map<String, Object>>) body.get("fieldErrors");
        assertThat(fieldErrors).isNotEmpty();
        assertThat(fieldErrors.get(0)).containsKeys("field", "message");
        assertThat(fieldErrors).anySatisfy(fe ->
                assertThat(List.of("email", "password")).contains((String) fe.get("field")));
    }
}
