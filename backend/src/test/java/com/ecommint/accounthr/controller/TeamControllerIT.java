package com.ecommint.accounthr.controller;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.TeamRepository;

/**
 * E3-06 — {@code GET /api/v1/teams} takım seçici (picker) endpoint testi.
 *
 * <ul>
 *   <li>Kimlik doğrulanmış istek → 200 + ada göre sıralı {@code [{ id, name }]} listesi.</li>
 *   <li>Kimliksiz istek → 401.</li>
 * </ul>
 *
 * {@link AbstractDataCleanupIT} her test öncesi/sonrası truncate ile izole eder →
 * herhangi bir surefire sırasında geçer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TeamControllerIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userEmail;

    @BeforeEach
    void seed() {
        AppUser user = new AppUser();
        userEmail = "team.picker@e-commint.com";
        user.setEmail(userEmail);
        user.setFullName("Takım Seçici");
        user.setRole(UserRole.TEAM_MEMBER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(user);

        // Sıralamayı doğrulamak için kasıtlı olarak alfabetik olmayan ekleme sırası.
        Team marketing = new Team();
        marketing.setName("Marketing");
        teamRepository.save(marketing);

        Team backend = new Team();
        backend.setName("Backend");
        teamRepository.save(backend);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token() {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", userEmail, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    @Test
    void listTeamsAuthenticatedReturnsIdAndNameOrderedByName() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token());

        ResponseEntity<List<Map<String, Object>>> resp = rest.exchange(
                "/api/v1/teams", HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = resp.getBody();
        assertThat(body).isNotNull().hasSize(2);

        // Ada göre alfabetik: Backend, Marketing.
        assertThat(body).extracting(t -> t.get("name")).containsExactly("Backend", "Marketing");
        // Her öğe yalnızca id + name içerir, id sayısal ve dolu.
        for (Map<String, Object> t : body) {
            assertThat(t).containsOnlyKeys("id", "name");
            assertThat(((Number) t.get("id")).longValue()).isPositive();
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    void listTeamsUnauthenticatedReturns401() {
        ResponseEntity<Map> resp = rest.getForEntity("/api/v1/teams", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
