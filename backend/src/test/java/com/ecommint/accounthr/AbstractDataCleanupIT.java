package com.ecommint.accounthr;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @SpringBootTest} ITs share ONE cached ApplicationContext and ONE H2
 * in-memory database. Any test that COMMITS rows leaks that data into every
 * subsequent test class. On CI the alphabetical-ish class order makes a
 * committing IT (e.g. {@code AuditCaptureIT}) run before {@code ServiceControllerIT},
 * whose {@code serviceRepository.deleteAll()} then trips the
 * {@code expenses.service_id → services.id} FK (H2 error 23503) because leaked
 * expense rows still reference those services.
 *
 * <p>This base class makes data-writing ITs self-isolating: {@code @BeforeEach}
 * and {@code @AfterEach} wipe ALL application tables in a FK-safe way. We disable
 * referential integrity for the duration of the wipe (H2/PostgreSQL mode) so the
 * delete order can never violate a constraint, then re-enable it. Running it both
 * before and after each test makes the suite pass in ANY execution order,
 * regardless of what previous classes committed.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class AbstractDataCleanupIT {

    /**
     * Child tables first is not strictly required because referential integrity is
     * disabled during the wipe, but the order is kept FK-correct for clarity and so
     * the same list works even if a future DB has integrity left on.
     */
    private static final List<String> TABLES_CHILD_FIRST = List.of(
            "files",
            "invoices",
            "raw_transactions",
            "expenses",
            "audit_log",
            "service_contacts",
            "service_credentials",
            "services",
            "providers",
            "cards",
            "refresh_tokens",
            "users",
            "periods",
            "teams");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void wipeBeforeEach() {
        wipeAllTables();
    }

    @AfterEach
    void wipeAfterEach() {
        wipeAllTables();
    }

    /**
     * Deletes every row from every application table with referential integrity
     * temporarily disabled, then restarts identity columns. Safe to run repeatedly
     * (idempotent) and order-independent.
     */
    protected void wipeAllTables() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : TABLES_CHILD_FIRST) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
