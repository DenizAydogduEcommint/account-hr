package com.ecommint.accounthr.auth;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.RefreshTokenRepository;

/**
 * E1-03 uçtan uca kimlik doğrulama akışı (H2, RANDOM_PORT, TestRestTemplate).
 * Flyway test profilinde KAPALI olduğu için kullanıcılar testte repository ile
 * (BCrypt-encode edilmiş parolayla) oluşturulur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private String memberEmail;

    @BeforeEach
    void seedUsers() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        AppUser admin = new AppUser();
        adminEmail = "admin.test@e-commint.com";
        admin.setEmail(adminEmail);
        admin.setFullName("Test Yönetici");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(admin);

        AppUser member = new AppUser();
        memberEmail = "member.test@e-commint.com";
        member.setEmail(memberEmail);
        member.setFullName("Test Ekip Üyesi");
        member.setRole(UserRole.TEAM_MEMBER);
        member.setActive(true);
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(member);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login",
                Map.of("email", email, "password", password),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // 2. login OK → 200 + tokens + user
    @Test
    @SuppressWarnings("unchecked")
    void loginReturnsTokensAndUser() {
        Map<String, Object> body = login(adminEmail, PASSWORD);

        assertThat(body.get("accessToken")).isInstanceOf(String.class);
        assertThat((String) body.get("accessToken")).isNotBlank();
        assertThat(body.get("refreshToken")).isInstanceOf(String.class);
        assertThat(body.get("tokenType")).isEqualTo("Bearer");
        assertThat(((Number) body.get("expiresIn")).longValue()).isEqualTo(900L);

        Map<String, Object> user = (Map<String, Object>) body.get("user");
        assertThat(user.get("email")).isEqualTo(adminEmail);
        assertThat(user.get("fullName")).isEqualTo("Test Yönetici");
        assertThat(user.get("role")).isEqualTo("ADMIN");
        assertThat(((Number) user.get("id")).longValue()).isPositive();
    }

    // 3. login wrong password → 401
    @Test
    @SuppressWarnings("rawtypes")
    void loginWithWrongPasswordReturns401() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/auth/login",
                Map.of("email", adminEmail, "password", "wrong"),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().get("error")).isEqualTo("UNAUTHORIZED");
        assertThat(resp.getBody().get("message")).isNotNull();
    }

    // 4. GET /api/auth/me with access token → 200
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void meWithAccessTokenReturnsUser() {
        String access = (String) login(adminEmail, PASSWORD).get("accessToken");

        ResponseEntity<Map> resp = rest.exchange(
                "/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer(access)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("email")).isEqualTo(adminEmail);
        assertThat(resp.getBody().get("role")).isEqualTo("ADMIN");
    }

    // 5. protected endpoint without token → 401
    @Test
    @SuppressWarnings("rawtypes")
    void protectedEndpointWithoutTokenReturns401() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/auth/me", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().get("error")).isEqualTo("UNAUTHORIZED");
    }

    // 6. refresh → new tokens; old refresh after rotation → 401
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void refreshRotatesTokenAndOldOneIsRejected() {
        Map<String, Object> first = login(adminEmail, PASSWORD);
        String oldRefresh = (String) first.get("refreshToken");

        ResponseEntity<Map> refreshResp = rest.postForEntity(
                "/api/auth/refresh", Map.of("refreshToken", oldRefresh), Map.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefresh = (String) refreshResp.getBody().get("refreshToken");
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);
        assertThat(refreshResp.getBody().get("accessToken")).isNotNull();

        // Eski refresh (artık iptal edilmiş) → 401
        ResponseEntity<Map> reuse = rest.postForEntity(
                "/api/auth/refresh", Map.of("refreshToken", oldRefresh), Map.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody().get("error")).isEqualTo("UNAUTHORIZED");
    }

    // logout revokes refresh token → 204, subsequent refresh → 401
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void logoutRevokesRefreshToken() {
        String refresh = (String) login(adminEmail, PASSWORD).get("refreshToken");

        ResponseEntity<Void> logoutResp = rest.postForEntity(
                "/api/auth/logout", Map.of("refreshToken", refresh), Void.class);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> afterLogout = rest.postForEntity(
                "/api/auth/refresh", Map.of("refreshToken", refresh), Map.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // 7. admin-only endpoint with TEAM_MEMBER → 403
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void adminOnlyEndpointForbiddenForTeamMember() {
        String memberAccess = (String) login(memberEmail, PASSWORD).get("accessToken");

        ResponseEntity<Map> resp = rest.exchange(
                "/api/auth/admin-check", HttpMethod.GET,
                new HttpEntity<>(bearer(memberAccess)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().get("error")).isEqualTo("FORBIDDEN");

        // ADMIN aynı uca erişebilmeli → 200
        String adminAccess = (String) login(adminEmail, PASSWORD).get("accessToken");
        ResponseEntity<Map> ok = rest.exchange(
                "/api/auth/admin-check", HttpMethod.GET,
                new HttpEntity<>(bearer(adminAccess)), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("ok")).isEqualTo(true);
    }
}
