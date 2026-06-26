package com.ecommint.accounthr.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.expense.ExpenseListResponse;
import com.ecommint.accounthr.dto.expense.ExpenseRow;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.repository.TeamRepository;
import com.ecommint.accounthr.service.ExpenseQueryService;

import jakarta.persistence.EntityManager;

/**
 * E3-03-1 — {@link ExpenseQueryService#list} ToOne N+1 yapısal-doğrulama testi.
 *
 * <p>{@code toRow} eşlemesi her satırda LAZY {@code @ManyToOne} ilişkilerine
 * ({@code service}, {@code service.provider}, {@code card}, {@code usingTeam})
 * dokunur. Düzeltme öncesi 50'lik bir sayfa ~200+ ek SELECT tetikliyordu (N+1).
 * Düzeltme: sayfa içeriği için TEK {@code findByIdInFetchingRefs} FETCH-JOIN
 * sorgusuyla tüm ToOne ilişkiler eager yüklenir.
 *
 * <p>Bu test Hibernate {@link Statistics} ile sayfa-başı SQL sorgu sayısını ölçer ve
 * sorgu sayısının SATIR SAYISIYLA BÜYÜMEDİĞİNİ doğrular: 5 satırlık ve 25 satırlık iki
 * sayfa AYNI sayıda sorgu üretir → N+1 yapısal olarak kaldırılmıştır. Sıra ve içerik
 * doğruluğu da ayrıca doğrulanır.
 *
 * <p>{@code @DataJpaTest} servis bean'ini yüklemez; {@code @Import} ile {@link
 * ExpenseQueryService} ve onun repository bağımlılıkları (hepsi JPA repo) wire edilir.
 */
@DataJpaTest
@Import({ JpaAuditingConfig.class, ExpenseQueryService.class })
@ActiveProfiles("test")
class ExpenseQueryServiceN1JpaTest {

    private static final String MONTH = "2026-08";

    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ServiceContactRepository serviceContactRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private com.ecommint.accounthr.repository.FileAssetRepository fileAssetRepository;
    @Autowired private ExpenseQueryService expenseQueryService;
    @Autowired private EntityManager entityManager;

    private Period period;
    private com.ecommint.accounthr.domain.Service service;
    private Card card;
    private Team team;

    private void seedCommon() {
        Provider provider = new Provider();
        provider.setName("Anthropic");
        providerRepository.save(provider);

        card = new Card();
        card.setBank("Akbank");
        card.setLastFour("3800");
        card.setHolderName("Kaan Bingöl");
        cardRepository.save(card);

        team = new Team();
        team.setName("Backend");
        teamRepository.save(team);

        service = new com.ecommint.accounthr.domain.Service();
        service.setName("Claude AI");
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service.setInformational(false);
        serviceRepository.save(service);

        ServiceContact contact = new ServiceContact();
        contact.setService(service);
        contact.setEmail("accounting@e-commint.com");
        contact.setPrimary(true);
        serviceContactRepository.save(contact);

        period = new Period();
        period.setYear(2026);
        period.setMonth(8);
        period.setCode(MONTH);
        periodRepository.save(period);
    }

    /** {@code count} adet ANA harcama (her biri bir FOUND invoice ile) ekler. */
    private void seedExpenses(int count) {
        for (int i = 0; i < count; i++) {
            Expense e = new Expense();
            e.setService(service);
            e.setPeriod(period);
            e.setCard(card);
            e.setUsingTeam(team);
            e.setTransactionDate(LocalDate.of(2026, 8, 1).plusDays(i));
            e.setAmount(new BigDecimal("10.00"));
            e.setCurrency(Currency.TRY);
            e.setAmountTry(new BigDecimal("10.00"));
            e.setPurpose("LLM API");
            e.setInformational(false);
            expenseRepository.save(e);

            Invoice inv = new Invoice();
            inv.setExpense(e);
            inv.setStatus(InvoiceStatus.FOUND);
            inv.setNote("faturalar/2026-08/claude_" + i + ".pdf");
            invoiceRepository.save(inv);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics statistics() {
        SessionFactory sf = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }

    private long countQueriesForPage(int pageSize) {
        Pageable pageable = PageRequest.of(0, pageSize,
                Sort.by(Sort.Direction.ASC, "transactionDate"));
        Statistics stats = statistics();
        stats.clear();
        ExpenseListResponse resp = expenseQueryService.list(MONTH, null, null, null, pageable);
        // Sayfa gerçekten dolu olmalı (ölçüm anlamlı kalsın diye).
        assertThat(resp.main().content()).hasSize(pageSize);
        return stats.getPrepareStatementCount();
    }

    @Test
    void perPageQueryCountIsBoundedAndDoesNotGrowWithRowCount() {
        seedCommon();
        seedExpenses(25);

        long smallPageQueries = countQueriesForPage(5);
        long largePageQueries = countQueriesForPage(25);

        // N+1 olsaydı 25 satırlık sayfa, 5 satırlıktan ÇOK daha fazla sorgu üretirdi
        // (her satır için service/provider/card/usingTeam erişimi). Yapısal düzeltmeyle
        // sorgu sayısı satır sayısından BAĞIMSIZDIR → iki sayfa AYNI sayıda sorgu yapar.
        assertThat(largePageQueries)
                .as("per-page query count must NOT grow with row count (N+1 removed)")
                .isEqualTo(smallPageQueries);

        // Üst sınır: küçük, sabit bir sorgu seti (page+count, ToOne fetch, rep-invoice
        // batch, email batch, iki dönem-toplamı, informational liste+toplamı). Satır
        // sayısıyla ölçeklenmediğini göstermek için gevşek ama sabit bir tavan.
        assertThat(largePageQueries)
                .as("bounded constant number of queries per page")
                .isLessThanOrEqualTo(12L);
    }

    /**
     * E3 deep-review #5 — {@link ExpenseQueryService#listFiles} bir expense'in BİRDEN ÇOK
     * invoice'unun dosyalarını TEK toplu sorguyla ({@code findByInvoiceIdIn}) çeker (per-invoice
     * {@code findByInvoiceId} döngüsü değil); sonuç invoice id ASC + file id ASC sıralı ve file
     * id'ye göre tekilleştirilmiştir. Burada 2 invoice + 3 dosya senaryosunda dosyaların
     * doğru/tam geldiğini doğrularız (sorgu sayısı invoice sayısıyla büyümez).
     */
    @Test
    void listFilesBatchesAcrossMultipleInvoices() {
        seedCommon();

        Expense e = new Expense();
        e.setService(service);
        e.setPeriod(period);
        e.setCard(card);
        e.setUsingTeam(team);
        e.setTransactionDate(LocalDate.of(2026, 8, 1));
        e.setAmount(new BigDecimal("10.00"));
        e.setCurrency(Currency.TRY);
        e.setAmountTry(new BigDecimal("10.00"));
        e.setInformational(false);
        expenseRepository.save(e);

        Invoice inv1 = new Invoice();
        inv1.setExpense(e);
        inv1.setStatus(InvoiceStatus.FOUND);
        invoiceRepository.save(inv1);

        Invoice inv2 = new Invoice();
        inv2.setExpense(e);
        inv2.setStatus(InvoiceStatus.E_INVOICE);
        invoiceRepository.save(inv2);

        // inv1: 2 dosya, inv2: 1 dosya → toplam 3 (file id'ler distinct).
        saveFile(inv1, "a.pdf", "aaa1");
        saveFile(inv1, "b.pdf", "bbb1");
        saveFile(inv2, "c.xml", "ccc1");
        entityManager.flush();
        entityManager.clear();

        Statistics stats = statistics();
        stats.clear();
        List<com.ecommint.accounthr.dto.file.ExpenseFileResponse> files =
                expenseQueryService.listFiles(e.getId());
        long queriesForFiles = stats.getPrepareStatementCount();

        // 3 dosya, doğru isimler, deterministik sıra (inv1 ASC: a,b; sonra inv2: c).
        assertThat(files).hasSize(3);
        assertThat(files.stream().map(com.ecommint.accounthr.dto.file.ExpenseFileResponse::fileName))
                .containsExactly("a.pdf", "b.pdf", "c.xml");

        // Toplu çekim: dosya-getirme sorgusu invoice SAYISIYLA büyümez (2 invoice → 1 IN sorgusu).
        // existsById + invoice listesi + tek findByInvoiceIdIn → küçük sabit set.
        assertThat(queriesForFiles)
                .as("listFiles must batch file fetch (no per-invoice query)")
                .isLessThanOrEqualTo(4L);
    }

    private void saveFile(Invoice invoice, String name, String sha) {
        com.ecommint.accounthr.domain.FileAsset f = new com.ecommint.accounthr.domain.FileAsset();
        f.setInvoice(invoice);
        f.setFilePath("2026-08/" + name);
        f.setFileName(name);
        f.setFileType(name.endsWith(".xml")
                ? com.ecommint.accounthr.domain.enums.FileType.XML
                : com.ecommint.accounthr.domain.enums.FileType.PDF);
        f.setMimeType(name.endsWith(".xml") ? "application/xml" : "application/pdf");
        f.setSizeBytes(10L);
        f.setSha256(sha);
        fileAssetRepository.save(f);
    }

    @Test
    void pageContentIsCorrectAndInTransactionDateOrder() {
        seedCommon();
        seedExpenses(10);

        Pageable pageable = PageRequest.of(0, 10,
                Sort.by(Sort.Direction.ASC, "transactionDate"));
        ExpenseListResponse resp = expenseQueryService.list(MONTH, null, null, null, pageable);

        List<ExpenseRow> rows = resp.main().content();
        assertThat(rows).hasSize(10);

        // Sıra: pageable sort'u (transactionDate ASC) korunmalı — fetch sorgusu
        // yeniden sıralamamalı.
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i).transactionDate())
                    .isEqualTo(LocalDate.of(2026, 8, 1).plusDays(i));
        }

        // ToOne ilişkiler eager yüklendi → tüm türetilmiş alanlar dolu (lazy hata yok).
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.serviceName()).isEqualTo("Claude AI");
            assertThat(row.providerName()).isEqualTo("Anthropic");
            assertThat(row.cardLast4()).isEqualTo("3800");
            assertThat(row.usingTeam()).isEqualTo("Backend");
            assertThat(row.accountingEmail()).isEqualTo("accounting@e-commint.com");
            assertThat(row.invoiceStatus()).isEqualTo(InvoiceStatus.FOUND);
        });

        // Operasyonel toplam = 10 * 10.00 = 100.00.
        assertThat(resp.operationalTotalTry()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
