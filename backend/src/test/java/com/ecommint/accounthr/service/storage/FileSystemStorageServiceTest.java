package com.ecommint.accounthr.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ecommint.accounthr.config.JpaAuditingConfig;
import com.ecommint.accounthr.config.StorageProperties;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Service;
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

/**
 * E1-04 depolama servisi davranış testleri (H2 + JUnit @TempDir).
 * Storage kökü ASLA gerçek STORAGE_ROOT / expenses/faturalar değil — her zaman
 * geçici dizine işaret eder (@DynamicPropertySource ile override).
 * Aynı pakette olduğu için package-private slugify/resolveUnderRoot test edilebilir.
 */
@DataJpaTest
@Import({ JpaAuditingConfig.class, FileSystemStorageService.class })
@EnableConfigurationProperties(StorageProperties.class)
@ActiveProfiles("test")
class FileSystemStorageServiceTest {

    /** Tüm test sınıfı için tek temp dizin; storage kökü buraya bağlanır. */
    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired private FileSystemStorageService storageService;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;

    @BeforeAll
    static void sanityCheckRootIsTemp() {
        // Güvenlik: kök kesinlikle expenses/faturalar değil.
        assertThat(storageRoot.toString()).doesNotContain("expenses/faturalar");
    }

    // ------------------------------------------------------------------
    // Yardımcı: bir invoice üret (provider + invoiceNo opsiyonel).
    // ------------------------------------------------------------------
    private Invoice newInvoice(Provider provider, String invoiceNo) {
        Card card = new Card();
        card.setBank("Akbank");
        card.setLastFour("38" + (System.nanoTime() % 100));
        card.setHolderName("Test");
        card = cardRepository.save(card);

        Service service = new Service();
        service.setName("Test Service " + System.nanoTime());
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        service.setApproxAmountTry(new BigDecimal("100.00"));
        service = serviceRepository.save(service);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(3);
        period.setCode("2026-03-" + System.nanoTime());
        period = periodRepository.save(period);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCard(card);
        expense.setTransactionDate(LocalDate.of(2026, 3, 1));
        expense.setAmount(new BigDecimal("20.00"));
        expense.setCurrency(Currency.USD);
        expense = expenseRepository.save(expense);

        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(provider);
        invoice.setStatus(InvoiceStatus.FOUND);
        invoice.setInvoiceNo(invoiceNo);
        invoice.setInvoiceDate(LocalDate.of(2026, 2, 28));
        invoice.setAmount(new BigDecimal("20.00"));
        invoice.setCurrency(Currency.USD);
        return invoiceRepository.save(invoice);
    }

    private Provider newProvider(String name) {
        Provider p = new Provider();
        p.setName(name);
        return providerRepository.save(p);
    }

    private FileAsset persistAsset(Invoice invoice, StoredFile stored, FileType type) {
        FileAsset asset = new FileAsset();
        asset.setInvoice(invoice);
        asset.setFilePath(stored.relativePath());
        asset.setFileName(stored.fileName());
        asset.setFileType(type);
        asset.setMimeType("application/pdf");
        asset.setSizeBytes(stored.sizeBytes());
        asset.setSha256(stored.sha256());
        return fileAssetRepository.save(asset);
    }

    // ------------------------------------------------------------------
    // Testler
    // ------------------------------------------------------------------

    @Test
    void store_usesInvoiceDateMonthFolder_andTurkishSlugifiedFileName() {
        Provider provider = newProvider("Google");
        Invoice invoice = newInvoice(provider, "G-001");

        // Fatura tarihi 28 Şubat → klasör 2026-02; ödeme Mart olsa bile.
        StoredFile stored = storageService.store(
                invoice.getId(), LocalDate.of(2026, 2, 28), "Google Workspace",
                provider.getId(), "G-001", "orig.pdf",
                new ByteArrayInputStream("pdf-bytes".getBytes(StandardCharsets.UTF_8)),
                FileType.PDF);

        assertThat(stored.relativePath()).isEqualTo("2026-02/google_workspace_subat.pdf");
        assertThat(stored.fileName()).isEqualTo("google_workspace_subat.pdf");
        assertThat(Files.exists(storageRoot.resolve("2026-02/google_workspace_subat.pdf"))).isTrue();
        assertThat(stored.sizeBytes()).isEqualTo("pdf-bytes".length());
        assertThat(stored.sha256()).hasSize(64);
    }

    @Test
    void store_onNameCollision_appendsSuffix() {
        Provider provider = newProvider("Collision Provider");
        Invoice invoice = newInvoice(provider, null); // invoiceNo null → mantıksal duplicate atlanır

        StoredFile first = storageService.store(
                invoice.getId(), LocalDate.of(2026, 4, 5), "Same Service",
                null, null, "a.pdf",
                new ByteArrayInputStream("content-A".getBytes(StandardCharsets.UTF_8)),
                FileType.PDF);
        StoredFile second = storageService.store(
                invoice.getId(), LocalDate.of(2026, 4, 5), "Same Service",
                null, null, "b.pdf",
                new ByteArrayInputStream("content-B-different".getBytes(StandardCharsets.UTF_8)),
                FileType.PDF);

        assertThat(first.fileName()).isEqualTo("same_service_nisan.pdf");
        assertThat(second.fileName()).isEqualTo("same_service_nisan_1.pdf");
        assertThat(second.relativePath()).isEqualTo("2026-04/same_service_nisan_1.pdf");
    }

    @Test
    void store_duplicateSha256_throwsDuplicateFileException() {
        Provider provider = newProvider("Dup SHA Provider");
        Invoice invoice = newInvoice(provider, null);

        byte[] identical = "exact-same-content".getBytes(StandardCharsets.UTF_8);

        StoredFile first = storageService.store(
                invoice.getId(), LocalDate.of(2026, 5, 1), "Dup Service",
                null, null, "x.pdf", new ByteArrayInputStream(identical), FileType.PDF);
        // İlk dosyanın FileAsset kaydını kalıcılaştır ki SHA kontrolü onu görsün.
        persistAsset(invoice, first, FileType.PDF);

        assertThatThrownBy(() -> storageService.store(
                invoice.getId(), LocalDate.of(2026, 5, 1), "Dup Service",
                null, null, "y.pdf", new ByteArrayInputStream(identical), FileType.PDF))
                .isInstanceOf(DuplicateFileException.class);
    }

    @Test
    void store_duplicateProviderAndInvoiceNo_throwsDuplicateFileException() {
        Provider provider = newProvider("Dup Logical Provider");
        Invoice invoice = newInvoice(provider, "INV-DUP-1");

        StoredFile first = storageService.store(
                invoice.getId(), LocalDate.of(2026, 6, 1), "Logical Service",
                provider.getId(), "INV-DUP-1", "a.pdf",
                new ByteArrayInputStream("aaa".getBytes(StandardCharsets.UTF_8)), FileType.PDF);
        persistAsset(invoice, first, FileType.PDF);

        // Aynı (provider, invoiceNo) → DuplicateFileException (içerik farklı olsa bile)
        assertThatThrownBy(() -> storageService.store(
                invoice.getId(), LocalDate.of(2026, 6, 1), "Logical Service",
                provider.getId(), "INV-DUP-1", "b.pdf",
                new ByteArrayInputStream("bbb-different".getBytes(StandardCharsets.UTF_8)), FileType.PDF))
                .isInstanceOf(DuplicateFileException.class);
    }

    @Test
    void moveToTrash_relocatesFile_andUpdatesPath() {
        Provider provider = newProvider("Trash Provider");
        Invoice invoice = newInvoice(provider, null);

        StoredFile stored = storageService.store(
                invoice.getId(), LocalDate.of(2026, 3, 10), "Trash Service",
                null, null, "f.pdf",
                new ByteArrayInputStream("trash-me".getBytes(StandardCharsets.UTF_8)), FileType.PDF);
        FileAsset asset = persistAsset(invoice, stored, FileType.PDF);
        Path original = storageRoot.resolve(stored.relativePath());
        assertThat(Files.exists(original)).isTrue();

        storageService.moveToTrash(asset.getId());

        FileAsset reloaded = fileAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(reloaded.getFilePath()).startsWith("trash/");
        assertThat(Files.exists(original)).isFalse();
        assertThat(Files.exists(storageRoot.resolve(reloaded.getFilePath()))).isTrue();
    }

    @Test
    void moveToWaiting_relocatesFile_andUpdatesPath() {
        Provider provider = newProvider("Waiting Provider");
        Invoice invoice = newInvoice(provider, null);

        StoredFile stored = storageService.store(
                invoice.getId(), LocalDate.of(2026, 3, 11), "Waiting Service",
                null, null, "w.pdf",
                new ByteArrayInputStream("please-wait".getBytes(StandardCharsets.UTF_8)), FileType.PDF);
        FileAsset asset = persistAsset(invoice, stored, FileType.PDF);
        Path original = storageRoot.resolve(stored.relativePath());

        storageService.moveToWaiting(asset.getId());

        FileAsset reloaded = fileAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(reloaded.getFilePath()).startsWith("waiting/");
        assertThat(Files.exists(original)).isFalse();
        assertThat(Files.exists(storageRoot.resolve(reloaded.getFilePath()))).isTrue();
    }

    @Test
    void store_persistsFileAssetRowWithHashAndSize() {
        Provider provider = newProvider("Persist Provider");
        Invoice invoice = newInvoice(provider, null);

        StoredFile stored = storageService.store(
                invoice.getId(), LocalDate.of(2026, 7, 1), "Persist Service",
                null, null, "p.pdf",
                new ByteArrayInputStream("persist-bytes".getBytes(StandardCharsets.UTF_8)), FileType.PDF);
        FileAsset asset = persistAsset(invoice, stored, FileType.PDF);

        FileAsset reloaded = fileAssetRepository.findById(asset.getId()).orElseThrow();
        assertThat(reloaded.getSha256()).isEqualTo(stored.sha256()).hasSize(64);
        assertThat(reloaded.getSizeBytes()).isEqualTo((long) "persist-bytes".length());
        assertThat(reloaded.getInvoice().getId()).isEqualTo(invoice.getId());
    }

    @Test
    void statementFileType_getsStatementSuffix() {
        Provider provider = newProvider("Statement Provider");
        Invoice invoice = newInvoice(provider, null);

        StoredFile stored = storageService.store(
                invoice.getId(), LocalDate.of(2026, 2, 15), "AWS",
                null, null, "stmt.pdf",
                new ByteArrayInputStream("statement".getBytes(StandardCharsets.UTF_8)),
                FileType.STATEMENT);

        assertThat(stored.fileName()).isEqualTo("aws_subat_statement.pdf");
    }

    @Test
    void xmlFileType_keepsXmlExtension() {
        Provider provider = newProvider("Xml Provider");
        Invoice invoice = newInvoice(provider, null);

        StoredFile stored = storageService.store(
                invoice.getId(), LocalDate.of(2026, 2, 15), "AWS",
                null, null, "fatura.xml",
                new ByteArrayInputStream("<xml/>".getBytes(StandardCharsets.UTF_8)),
                FileType.XML);

        assertThat(stored.fileName()).isEqualTo("aws_subat.xml");
    }

    @Test
    void resolveUnderRoot_rejectsPathTraversal() {
        assertThatThrownBy(() -> storageService.resolveUnderRoot("../../etc/passwd"))
                .isInstanceOf(StoragePathTraversalException.class);
        assertThatThrownBy(() -> storageService.resolveUnderRoot("2026-02/../../escape"))
                .isInstanceOf(StoragePathTraversalException.class);
    }

    @Test
    void slugify_handlesTurkishCharacters() {
        assertThat(FileSystemStorageService.slugify("Google Workspace")).isEqualTo("google_workspace");
        assertThat(FileSystemStorageService.slugify("İş Bankası")).isEqualTo("is_bankasi");
        assertThat(FileSystemStorageService.slugify("Çağrı Şöförü Ğ")).isEqualTo("cagri_soforu_g");
        // Güvensiz karakterler atılır, yol ayıracı sızdırılmaz.
        assertThat(FileSystemStorageService.slugify("a/b\\c..d")).isEqualTo("abcd");
    }
}
