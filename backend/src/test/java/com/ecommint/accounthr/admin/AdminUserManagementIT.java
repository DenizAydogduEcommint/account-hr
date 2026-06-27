package com.ecommint.accounthr.admin;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.RefreshToken;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.RefreshTokenRepository;

/**
 * E1-08 — Admin kullanıcı yönetimi (backoffice) uçtan-uca testleri.
 *
 * <p>5 ucu (list/create/password/role/active) ve güvenlik değişmezlerini doğrular:
 * parola hash'i yanıta sızmaz, parola değişimi refresh token'ları iptal eder,
 * son-aktif-yönetici koruması, rol-tabanlı yetkilendirme (403/401).
 *
 * <p>{@link AbstractDataCleanupIT} her test öncesi/sonrası tüm tabloları temizler;
 * her test ihtiyacını {@link #seed()} ile kurar → her surefire sırasında geçer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminUserManagementIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";
    private static final String BASE = "/api/v1/admin/users";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private String accountingEmail;
    private String memberEmail;
    private Long accountingId;

    @BeforeEach
    void seed() {
        adminEmail = "e108.admin@e-commint.com";
        accountingEmail = "e108.accounting@e-commint.com";
        memberEmail = "e108.member@e-commint.com";
        userRepository.save(user(adminEmail, UserRole.ADMIN));
        accountingId = userRepository.save(user(accountingEmail, UserRole.ACCOUNTING)).getId();
        userRepository.save(user(memberEmail, UserRole.TEAM_MEMBER));
    }

    private AppUser user(String email, UserRole role) {
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setFullName("E108 " + role);
        u.setRole(role);
        u.setActive(true);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return u;
    }

    // ------------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<String, Object> login(String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", password), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private String token(String email) {
        return (String) login(email, PASSWORD).get("accessToken");
    }

    private HttpHeaders json(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders jsonNoAuth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> exchange(HttpMethod method, String path, HttpHeaders headers, Object body) {
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    // ------------------------------------------------------------------------
    // 1. create user → 201, no passwordHash, in list, can log in
    // ------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void createUserReturns201WithoutPasswordHashAndCanLogin() {
        String newEmail = "fresh.user@e-commint.com";
        String newPass = "brandnew-pass-1";
        Map<String, Object> reqBody = Map.of(
                "email", newEmail, "fullName", "Fresh User", "role", "ACCOUNTING", "password", newPass);

        ResponseEntity<Map> created = exchange(HttpMethod.POST, BASE, json(adminEmail), reqBody);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> body = created.getBody();
        assertThat(body).doesNotContainKey("passwordHash");
        assertThat(body.get("email")).isEqualTo(newEmail);
        assertThat(body.get("role")).isEqualTo("ACCOUNTING");
        assertThat(body.get("active")).isEqualTo(true);
        assertThat(((Number) body.get("id")).longValue()).isPositive();

        // Appears in GET list (also no passwordHash in any entry).
        ResponseEntity<List> listResp = rest.exchange(
                BASE, HttpMethod.GET, new HttpEntity<>(json(adminEmail)), List.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> users = listResp.getBody();
        assertThat(users).anySatisfy(u -> assertThat(u.get("email")).isEqualTo(newEmail));
        assertThat(users).allSatisfy(u -> assertThat(u).doesNotContainKey("passwordHash"));

        // New user can log in with the chosen password.
        ResponseEntity<Map> loginResp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", newEmail, "password", newPass), Map.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------------
    // 2. duplicate email → 409
    // ------------------------------------------------------------------------

    @Test
    void duplicateEmailReturns409() {
        Map<String, Object> reqBody = Map.of(
                "email", accountingEmail, "fullName", "Dup", "role", "TEAM_MEMBER", "password", "longenough1");
        ResponseEntity<Map> resp = exchange(HttpMethod.POST, BASE, json(adminEmail), reqBody);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("error")).isEqualTo("DUPLICATE_EMAIL");
    }

    // ------------------------------------------------------------------------
    // 3. password < 8 → 400
    // ------------------------------------------------------------------------

    @Test
    void shortPasswordReturns400() {
        Map<String, Object> reqBody = Map.of(
                "email", "short.pw@e-commint.com", "fullName", "Short", "role", "TEAM_MEMBER", "password", "short");
        ResponseEntity<Map> resp = exchange(HttpMethod.POST, BASE, json(adminEmail), reqBody);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    // ------------------------------------------------------------------------
    // 4. invalid role string → 400
    // ------------------------------------------------------------------------

    @Test
    void invalidRoleReturns400() {
        Map<String, Object> reqBody = Map.of(
                "email", "bad.role@e-commint.com", "fullName", "Bad", "role", "GARBAGE", "password", "longenough1");
        ResponseEntity<Map> resp = exchange(HttpMethod.POST, BASE, json(adminEmail), reqBody);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    // ------------------------------------------------------------------------
    // 5. ADMIN-only: ACCOUNTING/TEAM_MEMBER → 403, no token → 401 (each endpoint)
    // ------------------------------------------------------------------------

    @Test
    void allEndpointsForbiddenForNonAdminAndUnauthenticated() {
        // GET list
        assertForbiddenAndUnauthorized(HttpMethod.GET, BASE, null);
        // POST create (minimal body; role gate runs before validation)
        Map<String, Object> createBody = Map.of(
                "email", "x@e-commint.com", "fullName", "X", "role", "TEAM_MEMBER", "password", "longenough1");
        assertForbiddenAndUnauthorized(HttpMethod.POST, BASE, createBody);
        // PATCH password
        assertForbiddenAndUnauthorized(HttpMethod.PATCH, BASE + "/" + accountingId + "/password",
                Map.of("password", "longenough1"));
        // PATCH role
        assertForbiddenAndUnauthorized(HttpMethod.PATCH, BASE + "/" + accountingId + "/role",
                Map.of("role", "TEAM_MEMBER"));
        // PATCH active
        assertForbiddenAndUnauthorized(HttpMethod.PATCH, BASE + "/" + accountingId + "/active",
                Map.of("active", false));
    }

    private void assertForbiddenAndUnauthorized(HttpMethod method, String path, Object body) {
        // ACCOUNTING → 403
        HttpStatusCode acc = exchange(method, path, json(accountingEmail), body).getStatusCode();
        assertThat(acc).as("ACCOUNTING on %s %s", method, path).isEqualTo(HttpStatus.FORBIDDEN);
        // TEAM_MEMBER → 403
        HttpStatusCode mem = exchange(method, path, json(memberEmail), body).getStatusCode();
        assertThat(mem).as("TEAM_MEMBER on %s %s", method, path).isEqualTo(HttpStatus.FORBIDDEN);
        // No token → 401
        HttpStatusCode anon = exchange(method, path, jsonNoAuth(), body).getStatusCode();
        assertThat(anon).as("anonymous on %s %s", method, path).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------------
    // 6. change password → 204 + refresh tokens revoked + old refresh rejected
    // ------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void changePasswordRevokesRefreshTokens() {
        // Target user (accounting) logs in → creates a refresh token.
        String oldRefresh = (String) login(accountingEmail, PASSWORD).get("refreshToken");
        assertThat(refreshTokenRepository.findAll()).anySatisfy(
                rt -> assertThat(rt.getUser().getId()).isEqualTo(accountingId));

        // Admin changes the target user's password → 204.
        ResponseEntity<Void> resp = rest.exchange(
                BASE + "/" + accountingId + "/password", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("password", "new-strong-pass"), json(adminEmail)), Void.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // All of that user's refresh tokens are now revoked.
        List<RefreshToken> theirTokens = refreshTokenRepository.findAll().stream()
                .filter(rt -> rt.getUser().getId().equals(accountingId))
                .toList();
        assertThat(theirTokens).isNotEmpty();
        assertThat(theirTokens).allSatisfy(rt -> assertThat(rt.isRevoked()).isTrue());

        // Old refresh token no longer works → 401.
        ResponseEntity<Map> reuse = rest.postForEntity(
                "/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh), Map.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // New password works.
        ResponseEntity<Map> loginNew = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", accountingEmail, "password", "new-strong-pass"), Map.class);
        assertThat(loginNew.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------------
    // 7. change role / change active → reflected
    // ------------------------------------------------------------------------

    @Test
    void changeRoleIsReflected() {
        ResponseEntity<Map> resp = exchange(HttpMethod.PATCH, BASE + "/" + accountingId + "/role",
                json(adminEmail), Map.of("role", "TEAM_MEMBER"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("role")).isEqualTo("TEAM_MEMBER");
        assertThat(userRepository.findById(accountingId).orElseThrow().getRole())
                .isEqualTo(UserRole.TEAM_MEMBER);
    }

    @Test
    void changeActiveIsReflected() {
        ResponseEntity<Map> resp = exchange(HttpMethod.PATCH, BASE + "/" + accountingId + "/active",
                json(adminEmail), Map.of("active", false));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("active")).isEqualTo(false);
        assertThat(userRepository.findById(accountingId).orElseThrow().isActive()).isFalse();
    }

    // ------------------------------------------------------------------------
    // 8. LAST-ADMIN protection
    // ------------------------------------------------------------------------

    @Test
    void demotingOnlyActiveAdminReturns409() {
        Long adminId = userRepository.findByEmail(adminEmail).orElseThrow().getId();
        ResponseEntity<Map> resp = exchange(HttpMethod.PATCH, BASE + "/" + adminId + "/role",
                json(adminEmail), Map.of("role", "TEAM_MEMBER"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("error")).isEqualTo("LAST_ADMIN");
        // Still ADMIN.
        assertThat(userRepository.findById(adminId).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void deactivatingOnlyActiveAdminReturns409() {
        Long adminId = userRepository.findByEmail(adminEmail).orElseThrow().getId();
        ResponseEntity<Map> resp = exchange(HttpMethod.PATCH, BASE + "/" + adminId + "/active",
                json(adminEmail), Map.of("active", false));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("error")).isEqualTo("LAST_ADMIN");
        assertThat(userRepository.findById(adminId).orElseThrow().isActive()).isTrue();
    }

    @Test
    void demotingOneOfTwoAdminsIsAllowed() {
        // Add a second active admin.
        Long secondAdminId = userRepository.save(user("second.admin@e-commint.com", UserRole.ADMIN)).getId();
        Long firstAdminId = userRepository.findByEmail(adminEmail).orElseThrow().getId();

        ResponseEntity<Map> resp = exchange(HttpMethod.PATCH, BASE + "/" + firstAdminId + "/role",
                json(adminEmail), Map.of("role", "TEAM_MEMBER"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("role")).isEqualTo("TEAM_MEMBER");
        // Exactly one active admin remains (post-mutation count check leaves it consistent).
        assertThat(userRepository.countByRoleAndActiveTrue(UserRole.ADMIN)).isEqualTo(1L);

        // Second admin remains the active admin; now demoting it → 409.
        Map<String, Object> body = new HashMap<>();
        body.put("role", "ACCOUNTING");
        ResponseEntity<Map> resp2 = exchange(HttpMethod.PATCH, BASE + "/" + secondAdminId + "/role",
                json("second.admin@e-commint.com"), body);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp2.getBody().get("error")).isEqualTo("LAST_ADMIN");
    }
}
