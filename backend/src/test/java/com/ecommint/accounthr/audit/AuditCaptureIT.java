package com.ecommint.accounthr.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.ecommint.accounthr.domain.AuditLog;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Service;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.AuditAction;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.repository.AuditLogRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E1-05: audit altyapısının uçtan uca doğrulaması (full context, H2, gerçek commit).
 *
 * <p>Mutasyonlar {@link AuditTestMutator} aracılığıyla AYRI {@code @Transactional}
 * metotlarda yapılır; her çağrı commit eder, böylece audit flush ({@code beforeCommit})
 * tetiklenir. Test metodu transactional DEĞİLDİR (rollback olsaydı flush çalışmazdı).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditCaptureIT {

    @TestConfiguration
    static class MutatorConfig {
        @Bean
        AuditTestMutator auditTestMutator(ServiceRepository s, ProviderRepository p,
                PeriodRepository pe, ExpenseRepository e, InvoiceRepository i) {
            return new AuditTestMutator(s, p, pe, e, i);
        }
    }

    /** Mutasyonları ayrı transaction'larda (commit ederek) yapan yardımcı bean. */
    @Component
    static class AuditTestMutator {
        private static final java.util.concurrent.atomic.AtomicInteger SEQ =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private final ServiceRepository services;
        private final ProviderRepository providers;
        private final PeriodRepository periods;
        private final ExpenseRepository expenses;
        private final InvoiceRepository invoices;

        AuditTestMutator(ServiceRepository services, ProviderRepository providers,
                PeriodRepository periods, ExpenseRepository expenses, InvoiceRepository invoices) {
            this.services = services;
            this.providers = providers;
            this.periods = periods;
            this.expenses = expenses;
            this.invoices = invoices;
        }

        @Transactional
        public Long createInvoiceExpectedStatus() {
            Provider provider = new Provider();
            provider.setName("Zoom-" + System.nanoTime());
            provider = providers.save(provider);

            Service service = new Service();
            service.setName("Zoom");
            service.setProvider(provider);
            service.setFrequency(Frequency.MONTHLY);
            service.setActiveState(ActiveState.YES);
            service = services.save(service);

            // Her çağrı için benzersiz (year, month) — uq_periods_year_month çakışmasın.
            int n = SEQ.incrementAndGet();
            int year = 2100 + n;
            Period period = new Period();
            period.setYear(year);
            period.setMonth(1);
            period.setCode("test-" + year + "-01");
            period = periods.save(period);

            Expense expense = new Expense();
            expense.setService(service);
            expense.setPeriod(period);
            expense.setTransactionDate(LocalDate.of(2026, 4, 1));
            expense.setAmount(new BigDecimal("15.00"));
            expense.setCurrency(Currency.USD);
            expense = expenses.save(expense);

            Invoice invoice = new Invoice();
            invoice.setExpense(expense);
            invoice.setStatus(InvoiceStatus.EXPECTED);
            invoice.setNote("ilk-not");
            invoice = invoices.save(invoice);
            return invoice.getId();
        }

        @Transactional
        public void changeStatusToFound(Long invoiceId) {
            Invoice invoice = invoices.findById(invoiceId).orElseThrow();
            invoice.setStatus(InvoiceStatus.FOUND);
        }
    }

    @Autowired private AuditTestMutator mutator;
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
    void statusChangeProducesStatusChangeAuditRowWithOldAndNew() {
        Long invoiceId = mutator.createInvoiceExpectedStatus();

        // CREATE audit (status değişimi henüz yok) sonrası temizle ki STATUS_CHANGE izole olsun.
        auditLogRepository.deleteAll();

        mutator.changeStatusToFound(invoiceId);

        List<AuditLog> rows = auditLogRepository.findAll();

        List<AuditLog> statusRows = rows.stream()
                .filter(r -> r.getAction() == AuditAction.STATUS_CHANGE)
                .toList();

        assertThat(statusRows).hasSize(1);
        AuditLog statusRow = statusRows.get(0);
        assertThat(statusRow.getEntityType()).isEqualTo("Invoice");
        assertThat(statusRow.getEntityId()).isEqualTo(invoiceId);
        assertThat(statusRow.getFieldName()).isEqualTo("status");
        assertThat(statusRow.getOldValue()).isEqualTo("EXPECTED");
        assertThat(statusRow.getNewValue()).isEqualTo("FOUND");
        assertThat(statusRow.getChangedAt()).isNotNull();
    }

    @Test
    void createProducesCreateAuditRow() {
        Long invoiceId = mutator.createInvoiceExpectedStatus();

        List<AuditLog> invoiceCreateRows = auditLogRepository.findAll().stream()
                .filter(r -> r.getEntityType().equals("Invoice")
                        && r.getAction() == AuditAction.CREATE)
                .toList();

        assertThat(invoiceCreateRows).hasSize(1);
        assertThat(invoiceCreateRows.get(0).getEntityId()).isEqualTo(invoiceId);
    }

    @Test
    void sensitiveFieldIsNeverCaptured() {
        Long invoiceId = mutator.createInvoiceExpectedStatus();
        auditLogRepository.deleteAll();
        mutator.changeStatusToFound(invoiceId);

        // Hiçbir audit satırında hassas alan adı / değeri olmamalı.
        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).isNotEmpty();
        for (AuditLog row : rows) {
            assertThat(row.getFieldName())
                    .isNotIn("password", "passwordHash", "secret", "token", "tokenHash");
        }
    }
}
