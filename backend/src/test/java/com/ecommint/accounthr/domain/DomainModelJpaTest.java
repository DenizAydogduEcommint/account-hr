package com.ecommint.accounthr.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

import jakarta.persistence.EntityManager;

/**
 * SERVICE-FIRST veri modelinin H2 üzerinde doğrulanması.
 * Provider→Service→Period→Card→Expense→Invoice→FileAsset zincirini kaydeder,
 * grafiğin yüklendiğini ve eksik-fatura sorgu temellerinin çalıştığını doğrular.
 *
 * Not: Flyway (Postgres SQL) testlerde kapalı; şema Hibernate tarafından entity'lerden
 * üretilir (ddl-auto=create-drop). Bu test eşleme + repository davranışını doğrular,
 * Postgres migrasyonunu DEĞİL.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class) // BaseEntity created/updated alanları için auditing gerekli
@ActiveProfiles("test")
class DomainModelJpaTest {

    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void savesAndLoadsFullServiceFirstGraph() {
        Provider provider = new Provider();
        provider.setName("Anthropic");
        provider = providerRepository.save(provider);

        Card card = new Card();
        card.setBank("Akbank");
        card.setLastFour("3800");
        card.setHolderName("Kaan Bingöl");
        card.setLabel("Akbank Axess");
        card = cardRepository.save(card);

        Service service = new Service();
        service.setName("Claude AI");
        service.setProvider(provider);
        service.setDefaultCard(card);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service.setApproxAmountTry(new BigDecimal("1200.00"));
        service = serviceRepository.save(service);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(1);
        period.setCode("2026-01");
        period = periodRepository.save(period);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCard(card);
        expense.setTransactionDate(LocalDate.of(2026, 1, 15));
        expense.setAmount(new BigDecimal("20.00"));
        expense.setCurrency(Currency.USD);
        expense.setAmountTry(new BigDecimal("700.00"));
        expense = expenseRepository.save(expense);

        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(provider);
        invoice.setStatus(InvoiceStatus.FOUND);
        invoice.setInvoiceNo("INV-2026-001");
        invoice.setInvoiceDate(LocalDate.of(2026, 1, 15));
        invoice.setAmount(new BigDecimal("20.00"));
        invoice.setCurrency(Currency.USD);
        invoice = invoiceRepository.save(invoice);

        FileAsset file = new FileAsset();
        file.setInvoice(invoice);
        file.setFilePath("faturalar/2026-01/claude_ocak.pdf");
        file.setFileName("claude_ocak.pdf");
        file.setFileType(FileType.PDF);
        file.setMimeType("application/pdf");
        file.setSizeBytes(12345L);
        file = fileAssetRepository.save(file);

        // Persistence context'i temizle ki gerçek bir DB yüklemesi olsun.
        entityManager.flush();
        entityManager.clear();

        // Grafiği taze yükle ve zincirin sağlam olduğunu doğrula.
        FileAsset loadedFile = fileAssetRepository.findById(file.getId()).orElseThrow();
        assertThat(loadedFile.getInvoice().getExpense().getService().getName()).isEqualTo("Claude AI");
        assertThat(loadedFile.getInvoice().getExpense().getService().getProvider().getName()).isEqualTo("Anthropic");
        assertThat(loadedFile.getInvoice().getExpense().getPeriod().getCode()).isEqualTo("2026-01");
        assertThat(loadedFile.getInvoice().getExpense().getCard().getLastFour()).isEqualTo("3800");

        // BaseEntity auditing alanları dolduruldu mu?
        assertThat(loadedFile.getInvoice().getExpense().getCreatedAt()).isNotNull();
        assertThat(loadedFile.getCreatedAt()).isNotNull();
    }

    @Test
    void existsByServiceIdAndPeriodId_provesMissingInvoiceCrossCheck() {
        Provider provider = new Provider();
        provider.setName("Contabo");
        provider = providerRepository.save(provider);

        Service service = new Service();
        service.setName("Contabo VPS");
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service = serviceRepository.save(service);

        Period jan = new Period();
        jan.setYear(2026);
        jan.setMonth(1);
        jan.setCode("2026-01");
        jan = periodRepository.save(jan);

        Period feb = new Period();
        feb.setYear(2026);
        feb.setMonth(2);
        feb.setCode("2026-02");
        feb = periodRepository.save(feb);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(jan);
        expense.setTransactionDate(LocalDate.of(2026, 1, 10));
        expense.setAmount(new BigDecimal("5.99"));
        expense.setCurrency(Currency.EUR);
        expenseRepository.save(expense);

        // Ocak'ta satır var → true; Şubat'ta yok → false (eksik fatura adayı).
        assertThat(expenseRepository.existsByServiceIdAndPeriodId(service.getId(), jan.getId())).isTrue();
        assertThat(expenseRepository.existsByServiceIdAndPeriodId(service.getId(), feb.getId())).isFalse();

        assertThat(expenseRepository.findByPeriodId(jan.getId())).hasSize(1);
    }

    @Test
    void findByActiveStateAndFrequency_returnsEveryActiveMonthlyService() {
        Provider provider = new Provider();
        provider.setName("AWS");
        provider = providerRepository.save(provider);

        Service activeMonthly = new Service();
        activeMonthly.setName("AWS");
        activeMonthly.setProvider(provider);
        activeMonthly.setFrequency(Frequency.MONTHLY);
        activeMonthly.setActiveState(ActiveState.YES);
        serviceRepository.save(activeMonthly);

        Service yearly = new Service();
        yearly.setName("Domain renewal");
        yearly.setProvider(provider);
        yearly.setFrequency(Frequency.YEARLY);
        yearly.setActiveState(ActiveState.YES);
        serviceRepository.save(yearly);

        Service inactiveMonthly = new Service();
        inactiveMonthly.setName("Old SaaS");
        inactiveMonthly.setProvider(provider);
        inactiveMonthly.setFrequency(Frequency.MONTHLY);
        inactiveMonthly.setActiveState(ActiveState.NO);
        serviceRepository.save(inactiveMonthly);

        List<Service> expected =
                serviceRepository.findByActiveStateAndFrequency(ActiveState.YES, Frequency.MONTHLY);

        assertThat(expected).extracting(Service::getName).containsExactly("AWS");
    }

    @Test
    void invoiceStatusQueryWorks() {
        Provider provider = new Provider();
        provider.setName("Zoom");
        provider = providerRepository.save(provider);

        Service service = new Service();
        service.setName("Zoom");
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service = serviceRepository.save(service);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(3);
        period.setCode("2026-03");
        period = periodRepository.save(period);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setTransactionDate(LocalDate.of(2026, 3, 1));
        expense.setAmount(new BigDecimal("15.00"));
        expense.setCurrency(Currency.USD);
        expense = expenseRepository.save(expense);

        Invoice expected = new Invoice();
        expected.setExpense(expense);
        expected.setStatus(InvoiceStatus.EXPECTED);
        invoiceRepository.save(expected);

        assertThat(invoiceRepository.findByStatus(InvoiceStatus.EXPECTED)).hasSize(1);
        assertThat(invoiceRepository.findByStatus(InvoiceStatus.FOUND)).isEmpty();
    }
}
