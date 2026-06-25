package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ecommint.accounthr.AbstractDataCleanupIT;
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
import com.ecommint.accounthr.dto.importer.InvoiceFileImportSummary;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E2-03 — {@link InvoiceFileImportService} davranış testi (H2 + JUnit @TempDir).
 *
 * <p>{@link AbstractDataCleanupIT}'i extend eder → test sırasından bağımsız geçer
 * (CI alphabetical). Storage kökü VE kaynak dizin geçici dizinlerdir; gerçek
 * STORAGE_ROOT ya da {@code expenses/faturalar} ASLA kullanılmaz.
 *
 * <p>Senaryo: kaynak {@code 2026-03/} altında {@code aws_mart.pdf} (note ile eşleşir)
 * + {@code aws_mart_statement.pdf} + {@code aws_mart.xml} (türev kardeşler) +
 * {@code orphan.pdf} (eşleşmeyen). {@code trash/x_duplicate.pdf} (unmatched + trashed +
 * duplicate). Seed: bir Invoice note = {@code faturalar/2026-03/aws_mart.pdf}.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvoiceFileImportIT extends AbstractDataCleanupIT {

    /** Storage kökü (kopyaların yazılacağı yer) — gerçek STORAGE_ROOT DEĞİL. */
    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    @Autowired private InvoiceFileImportService importService;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private FileAssetRepository fileAssetRepository;

    /** Kaynak (Drive aynası taklidi) — READ-ONLY beklenir. */
    @TempDir
    Path sourceDir;

    private Invoice awsInvoice;

    @BeforeEach
    void buildSourceAndSeed() throws IOException {
        // storageRoot @TempDir STATIC olduğundan test metotları arasında paylaşılır;
        // her testte temiz başla (fizik dosya sayımı/orphan assert'leri için zorunlu).
        cleanDirectoryContents(storageRoot);

        // --- Kaynak ağacı: 2026-03/ + trash/ ---
        Path month = Files.createDirectories(sourceDir.resolve("2026-03"));
        Path trash = Files.createDirectories(sourceDir.resolve("trash"));

        write(month.resolve("aws_mart.pdf"), "aws-mart-pdf-content");
        write(month.resolve("aws_mart_statement.pdf"), "aws-mart-statement-content");
        write(month.resolve("aws_mart.xml"), "<aws/>");
        write(month.resolve("orphan.pdf"), "orphan-no-invoice");
        write(trash.resolve("x_duplicate.pdf"), "trash-duplicate-content");
        // Atlanması gereken çöp: .DS_Store + 0-byte dosya.
        write(month.resolve(".DS_Store"), "junk");
        Files.createFile(month.resolve("empty.pdf")); // 0 byte → atlanır

        // --- Seed: bir Invoice, note = faturalar/2026-03/aws_mart.pdf ---
        awsInvoice = seedInvoiceWithNote("AWS", "faturalar/2026-03/aws_mart.pdf");
    }

    private void write(Path p, String content) throws IOException {
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    private Invoice seedInvoiceWithNote(String serviceName, String note) {
        Provider provider = new Provider();
        provider.setName(serviceName + " Provider");
        providerRepository.save(provider);

        Service service = new Service();
        service.setName(serviceName);
        service.setProvider(provider);
        service.setFrequency(Frequency.MONTHLY);
        service.setActiveState(ActiveState.YES);
        serviceRepository.save(service);

        Period period = new Period();
        period.setYear(2026);
        period.setMonth(3);
        period.setCode("2026-03");
        periodRepository.save(period);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCurrency(Currency.USD);
        expense.setAmount(new BigDecimal("100.00"));
        expenseRepository.save(expense);

        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(provider);
        invoice.setStatus(InvoiceStatus.EXPECTED);
        invoice.setNote(note);
        return invoiceRepository.save(invoice);
    }

    @Test
    void scanAndImport_copiesMatchesAndIsIdempotent() throws IOException {
        // Kaynak dosya sayısı + son değiştirilme zamanlarının baseline'ı.
        Path awsPdf = sourceDir.resolve("2026-03/aws_mart.pdf");
        Path awsStmt = sourceDir.resolve("2026-03/aws_mart_statement.pdf");
        Path awsXml = sourceDir.resolve("2026-03/aws_mart.xml");
        Path orphan = sourceDir.resolve("2026-03/orphan.pdf");
        Path trashDup = sourceDir.resolve("trash/x_duplicate.pdf");
        long awsPdfMtime = Files.getLastModifiedTime(awsPdf).toMillis();

        InvoiceFileImportSummary summary = importService.scanAndImport(sourceDir);

        // --- Sayımlar ---
        // 5 gerçek dosya taranır (.DS_Store + 0-byte atlanır):
        // aws_mart.pdf, aws_mart_statement.pdf, aws_mart.xml, orphan.pdf, trash/x_duplicate.pdf
        assertThat(summary.scanned()).isEqualTo(5);
        assertThat(summary.copied()).isEqualTo(5);
        assertThat(summary.newFileRows()).isEqualTo(5);
        // 3 aws dosyası AYNI invoice'a bağlanır (note + 2 türev).
        assertThat(summary.matched()).isEqualTo(3);
        // orphan + trash → unmatched (trash da unmatched sayılır).
        assertThat(summary.unmatched()).isEqualTo(2);
        assertThat(summary.trashed()).isEqualTo(1);
        // adı "duplicate" içeren trash dosyası.
        assertThat(summary.duplicates()).isEqualTo(1);
        assertThat(summary.unmatchedFiles())
                .containsExactlyInAnyOrder("2026-03/orphan.pdf", "trash/x_duplicate.pdf");

        // --- 3 aws dosyası AYNI invoice'a bağlı ---
        List<FileAsset> awsFiles = fileAssetRepository.findByInvoiceId(awsInvoice.getId());
        assertThat(awsFiles).hasSize(3);
        assertThat(awsFiles).extracting(FileAsset::getFileName)
                .containsExactlyInAnyOrder("aws_mart.pdf", "aws_mart_statement.pdf", "aws_mart.xml");
        assertThat(awsFiles).extracting(FileAsset::getFileType)
                .containsExactlyInAnyOrder(FileType.PDF, FileType.STATEMENT, FileType.XML);

        // --- Kopyalar storage kökü altında, göreli yol KORUNARAK ---
        assertThat(Files.exists(storageRoot.resolve("2026-03/aws_mart.pdf"))).isTrue();
        assertThat(Files.exists(storageRoot.resolve("2026-03/aws_mart_statement.pdf"))).isTrue();
        assertThat(Files.exists(storageRoot.resolve("2026-03/aws_mart.xml"))).isTrue();
        assertThat(Files.exists(storageRoot.resolve("2026-03/orphan.pdf"))).isTrue();
        assertThat(Files.exists(storageRoot.resolve("trash/x_duplicate.pdf"))).isTrue();

        // DB path'leri gerçek kopya konumuyla birebir.
        for (FileAsset f : fileAssetRepository.findAll()) {
            assertThat(Files.exists(storageRoot.resolve(f.getFilePath())))
                    .as("kopya mevcut: " + f.getFilePath()).isTrue();
        }

        // --- Kaynak dizine DOKUNULMADI (READ-ONLY) ---
        assertThat(Files.exists(awsPdf)).isTrue();
        assertThat(Files.exists(awsStmt)).isTrue();
        assertThat(Files.exists(awsXml)).isTrue();
        assertThat(Files.exists(orphan)).isTrue();
        assertThat(Files.exists(trashDup)).isTrue();
        assertThat(Files.getLastModifiedTime(awsPdf).toMillis()).isEqualTo(awsPdfMtime);
        // İçerik değişmedi.
        assertThat(Files.readString(awsPdf)).isEqualTo("aws-mart-pdf-content");

        // trash dosyası invoice'a bağlanMAMIŞ (unmatched/trashed).
        FileAsset trashAsset = fileAssetRepository.findByFilePath("trash/x_duplicate.pdf").get(0);
        assertThat(trashAsset.getInvoice()).isNull();

        // --- Idempotency: 2. çalıştırma 0 yeni dosya ---
        long rowsAfterFirst = fileAssetRepository.count();
        InvoiceFileImportSummary second = importService.scanAndImport(sourceDir);
        assertThat(second.newFileRows()).isZero();
        assertThat(second.copied()).isZero();
        assertThat(fileAssetRepository.count()).isEqualTo(rowsAfterFirst);

        // Hiçbir çalıştırmada orphan oluşmamalı: storage fizik dosya sayısı == DB satırı.
        assertThat(countPhysicalFiles(storageRoot)).isEqualTo(fileAssetRepository.count());
    }

    /**
     * İçerik-dedup (FIX 1): aynı byte içeriğine sahip İKİ fiziksel dosya farklı göreli
     * yollarda ({@code 2026-02/aws.pdf} ve {@code trash/aws_duplicate.pdf}) → storage'a
     * yalnızca BİRİ fiziken kopyalanmalı, o içerik için TAM OLARAK BİR {@code FileAsset}
     * satırı olmalı, ikincisi {@code duplicates} sayılmalı. Storage fizik dosya sayısı
     * DB satır sayısına eşit olmalı (orphan YOK).
     */
    @Test
    void contentDuplicate_isNotPhysicallyCopied_noOrphan() throws IOException {
        // @BeforeEach'in kurduğu ağacı temizle: bu test izole bir senaryo ister.
        deleteRecursively(sourceDir.resolve("2026-03"));
        deleteRecursively(sourceDir.resolve("trash"));
        invoiceRepository.deleteAll();

        // İki fiziken AYRI dosya, AYNI içerik, FARKLI göreli yol.
        Path feb = Files.createDirectories(sourceDir.resolve("2026-02"));
        Path trash = Files.createDirectories(sourceDir.resolve("trash"));
        String identicalContent = "identical-byte-content-aws-invoice";
        write(feb.resolve("aws.pdf"), identicalContent);
        write(trash.resolve("aws_duplicate.pdf"), identicalContent);

        InvoiceFileImportSummary summary = importService.scanAndImport(sourceDir);

        // Her iki dosya da tarandı; ikisi aynı içerik → tek satır, biri duplicate.
        assertThat(summary.scanned()).isEqualTo(2);
        assertThat(summary.copied()).isEqualTo(1);
        assertThat(summary.newFileRows()).isEqualTo(1);
        assertThat(summary.duplicates()).isEqualTo(1);

        // Bu içerik için TAM OLARAK BİR FileAsset satırı.
        assertThat(fileAssetRepository.count()).isEqualTo(1);

        // İlk taranan (sorted: "2026-02/aws.pdf" < "trash/...") storage'a kopyalandı;
        // duplicate FİZİKEN yazılMADI.
        assertThat(Files.exists(storageRoot.resolve("2026-02/aws.pdf"))).isTrue();
        assertThat(Files.exists(storageRoot.resolve("trash/aws_duplicate.pdf"))).isFalse();

        // Orphan YOK: storage fizik dosya sayısı == DB satır sayısı.
        assertThat(countPhysicalFiles(storageRoot)).isEqualTo(1L);
        assertThat(countPhysicalFiles(storageRoot)).isEqualTo(fileAssetRepository.count());

        // Kaynak DOKUNULMADI: her iki orijinal de yerinde.
        assertThat(Files.exists(feb.resolve("aws.pdf"))).isTrue();
        assertThat(Files.exists(trash.resolve("aws_duplicate.pdf"))).isTrue();

        // Idempotency: 2. çalıştırma 0 yeni satır, hâlâ orphan yok.
        InvoiceFileImportSummary second = importService.scanAndImport(sourceDir);
        assertThat(second.newFileRows()).isZero();
        assertThat(second.copied()).isZero();
        assertThat(fileAssetRepository.count()).isEqualTo(1);
        assertThat(countPhysicalFiles(storageRoot)).isEqualTo(1L);
    }

    /** Storage kökü altındaki gerçek dosya sayısı (dizinler hariç). */
    private long countPhysicalFiles(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    /** Dizinin İÇERİĞİNİ siler ama dizinin kendisini korur (kayıtlı storage kökü). */
    private void cleanDirectoryContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var children = Files.list(dir)) {
            for (Path child : children.toList()) {
                deleteRecursively(child);
            }
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
    }
}
