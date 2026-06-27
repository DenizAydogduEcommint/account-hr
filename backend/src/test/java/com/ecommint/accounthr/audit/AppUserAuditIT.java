package com.ecommint.accounthr.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.AuditLog;
import com.ecommint.accounthr.domain.enums.AuditAction;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.AuditLogRepository;

/**
 * E1-05 / güvenlik bulgusu 2 — {@code AppUser} mutasyonlarının audit izlenebilirliği.
 *
 * <p>Doğrular:
 * <ul>
 *   <li>Kullanıcı oluşturma → "AppUser" tipinde CREATE audit satırı.</li>
 *   <li>Rol değişimi → eski→yeni rolü gösteren UPDATE audit satırı.</li>
 *   <li><b>Hiçbir audit satırında passwordHash (alan adı VEYA değer) bulunmaz</b> —
 *       ne CREATE ne UPDATE yolunda parola hash'i audit_log'a sızar.</li>
 * </ul>
 *
 * <p>{@link AuditCaptureIT} ile aynı desen: mutasyonlar AYRI {@code @Transactional}
 * metotlarda commit edilir, böylece audit flush ({@code beforeTransactionCompletion})
 * tetiklenir. Test metodu transactional DEĞİLDİR.
 */
@SpringBootTest
@ActiveProfiles("test")
class AppUserAuditIT extends AbstractDataCleanupIT {

    @TestConfiguration
    static class MutatorConfig {
        @Bean
        AppUserAuditMutator appUserAuditMutator(AppUserRepository users) {
            return new AppUserAuditMutator(users);
        }
    }

    /** AppUser mutasyonlarını ayrı transaction'larda (commit ederek) yapan yardımcı bean. */
    @Component
    static class AppUserAuditMutator {
        // BCrypt hash'i benzeri tipik bir string — audit'e sızarsa testte yakalanır.
        static final String SECRET_HASH = "$2a$10$abcdefghijklmnopqrstuvDUMMYbcryptHASHvalue1234567890";

        private final AppUserRepository users;

        AppUserAuditMutator(AppUserRepository users) {
            this.users = users;
        }

        @Transactional
        public Long createUser(String email) {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setFullName("Audit Test User");
            u.setRole(UserRole.TEAM_MEMBER);
            u.setActive(true);
            u.setPasswordHash(SECRET_HASH);
            return users.save(u).getId();
        }

        @Transactional
        public void changeRoleToAccounting(Long id) {
            AppUser u = users.findById(id).orElseThrow();
            u.setRole(UserRole.ACCOUNTING);
        }

        @Transactional
        public void changePasswordHash(Long id) {
            AppUser u = users.findById(id).orElseThrow();
            u.setPasswordHash("$2a$10$DIFFERENThashvalueXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        }
    }

    @Autowired private AppUserAuditMutator mutator;
    @Autowired private AuditLogRepository auditLogRepository;

    @BeforeEach
    void clear() {
        auditLogRepository.deleteAll();
    }

    @AfterEach
    void cleanThreadLocal() {
        AuditContext.clear();
    }

    @Test
    void creatingUserWritesAppUserCreateAuditRowWithNoPasswordHash() {
        Long userId = mutator.createUser("audit.create@e-commint.com");

        List<AuditLog> createRows = auditLogRepository.findAll().stream()
                .filter(r -> "AppUser".equals(r.getEntityType())
                        && r.getAction() == AuditAction.CREATE)
                .toList();

        assertThat(createRows).hasSize(1);
        AuditLog row = createRows.get(0);
        assertThat(row.getEntityId()).isEqualTo(userId);
        // CREATE yolu hiçbir alan değeri yakalamaz → passwordHash sızamaz.
        assertThat(row.getFieldName()).isNull();
        assertThat(row.getOldValue()).isNull();
        assertThat(row.getNewValue()).isNull();
        assertNoPasswordHashLeak();
    }

    @Test
    void changingRoleWritesUpdateAuditRowWithOldAndNewRole() {
        Long userId = mutator.createUser("audit.role@e-commint.com");
        // CREATE satırını temizle ki UPDATE izole olsun.
        auditLogRepository.deleteAll();

        mutator.changeRoleToAccounting(userId);

        List<AuditLog> roleRows = auditLogRepository.findAll().stream()
                .filter(r -> "AppUser".equals(r.getEntityType())
                        && r.getAction() == AuditAction.UPDATE
                        && "role".equals(r.getFieldName()))
                .toList();

        assertThat(roleRows).hasSize(1);
        AuditLog row = roleRows.get(0);
        assertThat(row.getEntityId()).isEqualTo(userId);
        assertThat(row.getOldValue()).isEqualTo("TEAM_MEMBER");
        assertThat(row.getNewValue()).isEqualTo("ACCOUNTING");
        assertNoPasswordHashLeak();
    }

    @Test
    void changingPasswordHashIsNeverWrittenToAuditLog() {
        Long userId = mutator.createUser("audit.pw@e-commint.com");
        auditLogRepository.deleteAll();

        // passwordHash değişir → SENSITIVE_FIELDS filtresi UPDATE yolunda atlamalı.
        mutator.changePasswordHash(userId);

        List<AuditLog> rows = auditLogRepository.findAll();
        // passwordHash alanı için HİÇBİR audit satırı yazılmamalı.
        assertThat(rows).noneSatisfy(r ->
                assertThat(r.getFieldName()).isEqualTo("passwordHash"));
        assertNoPasswordHashLeak();
    }

    /**
     * Hiçbir audit satırının alan adı/eski/yeni değerinde parola hash'i (ne alan adı
     * "passwordHash" ne de gerçek hash string'i) bulunmamalı.
     */
    private void assertNoPasswordHashLeak() {
        List<AuditLog> rows = auditLogRepository.findAll();
        for (AuditLog row : rows) {
            assertThat(row.getFieldName()).isNotEqualTo("passwordHash");
            if (row.getOldValue() != null) {
                assertThat(row.getOldValue()).doesNotContain(AppUserAuditMutator.SECRET_HASH);
            }
            if (row.getNewValue() != null) {
                assertThat(row.getNewValue()).doesNotContain(AppUserAuditMutator.SECRET_HASH);
            }
        }
    }
}
