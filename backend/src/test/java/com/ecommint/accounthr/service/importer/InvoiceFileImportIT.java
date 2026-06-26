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

        // Period code tekildir; aynı testte birden çok kez çağrılırsa var olanı yeniden kullan.
        Period period = periodRepository.findByCode("2026-03").orElseGet(() -> {
            Period p = new Period();
            p.setYear(2026);
            p.setMonth(3);
            p.setCode("2026-03");
            return periodRepository.save(p);
        });

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

    /**
     * E2-DR-1: AYNI içerikli iki dosya FARKLI iki faturaya eşleşiyorsa, artık HER İKİSİ de
     * kendi {@link FileAsset} satırına BAĞLANIR (eskiden ikincisi "içerik-aynı, başka faturaya
     * bağlı" diye unmatched raporlanıyordu). Fizik dosya yine BİR KEZ saklanır: ikinci satır
     * birincinin {@code filePath}'ini paylaşır (bytes tekrar kopyalanmaz).
     */
    @Test
    void identicalContentMatchingTwoDifferentInvoices_bothAttached() throws IOException {
        deleteRecursively(sourceDir.resolve("2026-03"));
        deleteRecursively(sourceDir.resolve("trash"));
        invoiceRepository.deleteAll();

        // İki AYRI fatura, FARKLI note path'leri.
        Invoice invA = seedInvoiceWithNote("ServiceA", "faturalar/2026-03/svc_a.pdf");
        Invoice invB = seedInvoiceWithNote("ServiceB", "faturalar/2026-03/svc_b.pdf");

        // İki FİZİKEN AYRI dosya, AYNI içerik; her biri ayrı faturanın note'una eşleşir.
        Path month = Files.createDirectories(sourceDir.resolve("2026-03"));
        String identical = "byte-identical-shared-content";
        write(month.resolve("svc_a.pdf"), identical);
        write(month.resolve("svc_b.pdf"), identical);

        InvoiceFileImportSummary summary = importService.scanAndImport(sourceDir);

        // İlk dosya (sorted: svc_a < svc_b) fiziken kopyalandı; ikincisi mevcut path'i paylaştı.
        assertThat(summary.copied()).isEqualTo(1);          // tek fizik kopya
        assertThat(summary.newFileRows()).isEqualTo(2);     // iki DB satırı
        assertThat(summary.matched()).isEqualTo(2);         // iki faturaya da bağlandı
        assertThat(summary.unmatched()).isZero();
        // E2-DR-1 sayaç düzeltmesi: svc_b içeriği svc_a ile AYNI olsa da FARKLI bir faturaya
        // MEŞRU yeni satır olarak bağlanır (çapraz-fatura paylaşımı) → bu bir gerçek-duplicate
        // (genuine skip) DEĞİLDİR ve duplicate SAYILMAZ. Sayaç yalnızca atlanan satırlarda artar.
        assertThat(summary.duplicates()).isZero();

        // HER İKİ fatura da kendi FileAsset satırına sahip.
        List<FileAsset> aFiles = fileAssetRepository.findByInvoiceId(invA.getId());
        List<FileAsset> bFiles = fileAssetRepository.findByInvoiceId(invB.getId());
        assertThat(aFiles).hasSize(1);
        assertThat(bFiles).hasSize(1);

        // Fizik dosya BİR KEZ saklandı: yalnızca svc_a.pdf diskte; svc_b.pdf YOK.
        assertThat(Files.exists(storageRoot.resolve("2026-03/svc_a.pdf"))).isTrue();
        assertThat(Files.exists(storageRoot.resolve("2026-03/svc_b.pdf"))).isFalse();
        // İki satır AYNI fizik path'i paylaşır.
        assertThat(bFiles.get(0).getFilePath()).isEqualTo(aFiles.get(0).getFilePath());

        // İki DB satırı, tek fizik dosya (orphan yok, paylaşımlı).
        assertThat(fileAssetRepository.count()).isEqualTo(2);
        assertThat(countPhysicalFiles(storageRoot)).isEqualTo(1L);
    }

    /**
     * E2-DR-1 (fatura-içi dedup): AYNI faturaya eşleşen iki byte-aynı dosya → yalnızca BİR
     * {@link FileAsset} satırı (bileşik tekil / fatura-içi dedup). İkinci dosya gerçek-duplicate
     * sayılır; kullanıcıya hata YANSImaz (mevcut dedup davranışı korunur).
     */
    @Test
    void sameInvoiceSameContentTwice_onlyOneFileAsset() throws IOException {
        deleteRecursively(sourceDir.resolve("2026-03"));
        deleteRecursively(sourceDir.resolve("trash"));
        invoiceRepository.deleteAll();

        // Tek fatura; note tam tabana (aws_mart) işaret eder → kardeş (_statement) da AYNI faturaya.
        Invoice inv = seedInvoiceWithNote("SoloService", "faturalar/2026-03/aws_mart.pdf");

        Path month = Files.createDirectories(sourceDir.resolve("2026-03"));
        String identical = "same-content-same-invoice";
        // İki dosya AYNI içerik, AYNI klasör+taban (aws_mart) → ikisi de inv'e eşleşir.
        write(month.resolve("aws_mart.pdf"), identical);
        write(month.resolve("aws_mart_statement.pdf"), identical);

        InvoiceFileImportSummary summary = importService.scanAndImport(sourceDir);

        // Tek fizik kopya, tek DB satırı: ikinci (aynı fatura+sha) gerçek-duplicate → atlandı.
        assertThat(summary.copied()).isEqualTo(1);
        assertThat(summary.newFileRows()).isEqualTo(1);
        assertThat(summary.duplicates()).isEqualTo(1);
        assertThat(fileAssetRepository.findByInvoiceId(inv.getId())).hasSize(1);
        assertThat(fileAssetRepository.count()).isEqualTo(1);
        assertThat(countPhysicalFiles(storageRoot)).isEqualTo(1L);
    }

    /**
     * FIX 3: iki invoice note'u AYNI folderBaseKey'e düşerse (ör. {@code aws_mart.pdf} ve
     * {@code aws_mart_statement.pdf} → ikisi de baseKey {@code 2026-03/aws_mart}), bir
     * kardeş dosya ({@code aws_mart.xml}, tam note eşleşmesi YOK) YANLIŞ faturaya
     * bağlanmamalı. Tam-taban note'lu fatura tercih edilir; belirsizse kardeş unmatched olur.
     */
    @Test
    void siblingIsNotMisattachedWhenTwoNotesCollideOnBaseKey() throws IOException {
        deleteRecursively(sourceDir.resolve("2026-03"));
        deleteRecursively(sourceDir.resolve("trash"));
        invoiceRepository.deleteAll();

        // İki fatura, note'ları AYNI baseKey'e düşer: biri tam taban, biri türev (_statement).
        Invoice exact = seedInvoiceWithNote("ExactBase", "faturalar/2026-03/aws_mart.pdf");
        Invoice derived = seedInvoiceWithNote("DerivedBase", "faturalar/2026-03/aws_mart_statement.pdf");

        Path month = Files.createDirectories(sourceDir.resolve("2026-03"));
        // Her note'un kendi dosyası (tam eşleşir) + bir KARDEŞ (.xml, tam note eşleşmesi yok).
        write(month.resolve("aws_mart.pdf"), "a");
        write(month.resolve("aws_mart_statement.pdf"), "b");
        write(month.resolve("aws_mart.xml"), "c");

        importService.scanAndImport(sourceDir);

        // Tam note eşleşmeleri doğru: .pdf → exact, _statement.pdf → derived.
        assertThat(fileAssetRepository.findByInvoiceId(exact.getId()))
                .extracting(FileAsset::getFileName)
                .contains("aws_mart.pdf");
        assertThat(fileAssetRepository.findByInvoiceId(derived.getId()))
                .extracting(FileAsset::getFileName)
                .contains("aws_mart_statement.pdf");

        // Kardeş aws_mart.xml: baseKey çakışmasında TAM TABAN'lı (exact) fatura tercih edilir
        // → türev (derived) faturaya YANLIŞ bağlanmaz.
        FileAsset xml = fileAssetRepository.findByFilePath("2026-03/aws_mart.xml").get(0);
        Long xmlInvoiceId = xml.getInvoice() != null ? xml.getInvoice().getId() : null;
        assertThat(xmlInvoiceId).isNotEqualTo(derived.getId());
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
