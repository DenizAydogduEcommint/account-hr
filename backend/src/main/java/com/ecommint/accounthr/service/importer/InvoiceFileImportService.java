package com.ecommint.accounthr.service.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.dto.importer.InvoiceFileImportSummary;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.service.storage.StorageService;
import com.ecommint.accounthr.service.storage.StoredFile;

/**
 * E2-03 — {@code faturalar/} klasörlerini (ay klasörleri + {@code waiting/} + {@code trash/})
 * recursive tarayıp her fizik dosyayı storage kökü altına KOPYALAR ve uygun
 * {@code invoice}/{@code expense} ile eşler.
 *
 * <h2>Drive aynası dokunulmazdır</h2>
 * Kaynak dizin ({@code expenses/faturalar}) Drive aynasıdır; bu servis oradan YALNIZCA
 * OKUR. Hiçbir dosya kaynağa yazılmaz/silinmez/taşınmaz. Tüm yazma işlemleri storage
 * kökü ({@code STORAGE_ROOT}) içine, kaynaktaki göreli yolu AYNEN koruyarak yapılır
 * (migrasyon modu — yeniden adlandırma / yeniden klasörleme YOK; E1-04'ün fatura-tarihi
 * kuralı burada UYGULANMAZ).
 *
 * <h2>Eşleme önceliği</h2>
 * <ol>
 *   <li>Dosyanın göreli yolu bir {@code invoice.note} değerine eşitse → o invoice'a bağla.
 *       Notlar {@code faturalar/2026-03/aws_mart.pdf} biçimindedir; baştaki
 *       {@code faturalar/} öneki normalize edilerek atılır.</li>
 *   <li>Aynı klasör + aynı taban ada (ör. {@code aws_mart_statement.pdf}, {@code aws_mart.xml}
 *       taban {@code aws_mart}) sahip türev/kardeş dosya → AYNI invoice'a bağla
 *       (bir expense → çok dosya).</li>
 *   <li>Aksi halde eşleşmez (unmatched).</li>
 * </ol>
 * {@code trash/} altındaki dosyalar bilerek bağlanmaz (unmatched + trashed).
 *
 * <h2>Idempotency</h2>
 * Yeniden çalıştırma {@code files} satırlarını çiftlemez ve yeniden kopyalamaz: aynı
 * SHA-256 zaten DB'de varsa o fizik dosya atlanır (2. run'da 0 yeni satır).
 */
@Service
public class InvoiceFileImportService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceFileImportService.class);

    private static final String DS_STORE = ".DS_Store";
    private static final String TRASH_DIR = "trash";
    /** invoice.note path'lerinin başındaki bu önek atılır (DB: faturalar/2026-03/x.pdf). */
    private static final String NOTE_PATH_PREFIX = "faturalar/";

    /** Taban ad türetirken atılan tip ekleri (note dosyasıyla kardeşlik kurmak için). */
    private static final String[] DERIVED_SUFFIXES = {"_statement", "_receipt"};

    private final StorageService storageService;
    private final InvoiceRepository invoiceRepository;
    private final FileAssetRepository fileAssetRepository;

    public InvoiceFileImportService(StorageService storageService,
                                    InvoiceRepository invoiceRepository,
                                    FileAssetRepository fileAssetRepository) {
        this.storageService = storageService;
        this.invoiceRepository = invoiceRepository;
        this.fileAssetRepository = fileAssetRepository;
    }

    /**
     * Verilen kaynak dizini (Drive aynası, READ-ONLY) tarar, dosyaları storage kökü
     * altına kopyalar, invoice'larla eşler ve {@code files} kayıtları yazar.
     *
     * @param sourceDir taranacak kaynak kök (ör. {@code /Users/.../expenses/faturalar})
     * @return özet rapor (taranan/kopyalanan/eşleşen/eşleşmeyen/duplicate sayıları + liste)
     */
    @Transactional
    public InvoiceFileImportSummary scanAndImport(Path sourceDir) {
        if (sourceDir == null || !Files.isDirectory(sourceDir)) {
            throw new InvoiceFileImportException("Kaynak dizin yok ya da klasör değil: " + sourceDir);
        }
        Path source = sourceDir.toAbsolutePath().normalize();

        // --- Eşleme indeksleri (note path + türev taban ad → invoice) ---
        Map<String, Invoice> noteIndex = new HashMap<>();
        Map<String, Invoice> baseIndex = new HashMap<>();
        for (Invoice invoice : invoiceRepository.findByNoteIsNotNull()) {
            String rel = normalizeNotePath(invoice.getNote());
            if (rel == null) {
                continue; // path değil, serbest açıklama → atla
            }
            noteIndex.putIfAbsent(rel, invoice);
            String baseKey = folderBaseKey(rel);
            if (baseKey != null) {
                baseIndex.putIfAbsent(baseKey, invoice);
            }
        }

        int scanned = 0;
        int copied = 0;
        int matched = 0;
        int unmatched = 0;
        int trashed = 0;
        int duplicates = 0;
        int newFileRows = 0;
        List<String> unmatchedFiles = new ArrayList<>();
        Set<String> shaSeenThisRun = new HashSet<>();

        List<Path> files = collectFiles(source);
        for (Path file : files) {
            scanned++;
            String relPath = source.relativize(file).toString().replace('\\', '/');
            String fileName = file.getFileName().toString();
            FileType fileType = detectFileType(fileName);

            // --- ÖNCE SHA-256 hesapla (kaynağı bir kez oku, KOPYALAMA) ---
            // Kritik: content-dedup atlanan dosyalar storage köküne FİZİKEN yazılMAMALI.
            // copyPreservingPath farklı göreli yol için atlamadığından, kopyayı kaynak içerik
            // zaten kalıcıysa (DB'de aynı SHA-256) hiç yapmamalıyız. Aksi halde DB satırı
            // olmayan orphan fizik dosya kalır.
            String sha256 = computeSha256(file);

            boolean alreadyInDb = fileAssetRepository.existsBySha256(sha256);
            boolean sameRunDuplicate = shaSeenThisRun.contains(sha256);

            // --- Duplicate tespiti (raporlama amaçlı) ---
            boolean isDuplicate = fileName.toLowerCase(Locale.ROOT).contains("duplicate")
                    || sameRunDuplicate
                    || alreadyInDb;
            if (isDuplicate) {
                duplicates++;
            }

            // --- İçerik-dedup: aynı SHA-256 zaten kalıcıysa (DB'de ya da bu run'da)
            //     YENİ satır YAZMA, KOPYALAMA, sayma. Böylece her içerik için tam olarak
            //     bir fizik kopya + bir DB satırı kalır; orphan oluşmaz. ---
            // Re-run'da bu yol her dosya için çalışır → copied/matched/... hepsi 0,
            // newFileRows 0. Böylece "2. çalıştırma 0 yeni dosya" kanıtlanır.
            if (alreadyInDb || sameRunDuplicate) {
                continue;
            }
            shaSeenThisRun.add(sha256);

            // --- Yeni içerik → şimdi (ve yalnızca şimdi) fiziken kopyala ---
            StoredFile stored = copyToStorage(file, relPath);
            copied++; // DB'de yoktu → fiilen (ilk kez) kopyalandı

            // --- Eşleme ---
            boolean inTrash = isUnderTrash(relPath);
            Invoice match = inTrash ? null : findMatch(relPath, noteIndex, baseIndex);

            FileAsset asset = new FileAsset();
            asset.setInvoice(match);
            asset.setFilePath(stored.relativePath());
            asset.setFileName(stored.fileName());
            asset.setFileType(fileType);
            asset.setMimeType(probeMime(file, fileType));
            asset.setSizeBytes(stored.sizeBytes());
            asset.setSha256(sha256);
            // uploadedBy = null (system migration)
            fileAssetRepository.save(asset);
            newFileRows++;

            if (inTrash) {
                trashed++;
                unmatched++;
                unmatchedFiles.add(relPath);
            } else if (match != null) {
                matched++;
            } else {
                unmatched++;
                unmatchedFiles.add(relPath);
            }
        }

        log.info("E2-03 import: scanned={} copied={} matched={} unmatched={} trashed={} "
                        + "duplicates={} newFileRows={}",
                scanned, copied, matched, unmatched, trashed, duplicates, newFileRows);

        return new InvoiceFileImportSummary(
                scanned, copied, matched, unmatched, trashed, duplicates, newFileRows, unmatchedFiles);
    }

    // ------------------------------------------------------------------------
    // Tarama / kopyalama
    // ------------------------------------------------------------------------

    /** Kaynak ağacındaki gerçek dosyaları toplar (dizin/dotfile/.DS_Store/0-byte hariç). */
    private List<Path> collectFiles(Path source) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(source)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !isJunk(p))
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            throw new InvoiceFileImportException("Kaynak dizin taranamadı: " + source, e);
        }
        return result;
    }

    /** Dotfile, .DS_Store ve 0-byte dosyaları atla. */
    private boolean isJunk(Path p) {
        String name = p.getFileName().toString();
        if (name.equals(DS_STORE) || name.startsWith(".")) {
            return true;
        }
        try {
            return Files.size(p) == 0L;
        } catch (IOException e) {
            // boyutu okunamıyorsa güvenli tarafta kal: atla.
            log.warn("Dosya boyutu okunamadı, atlanıyor: {}", p, e);
            return true;
        }
    }

    private StoredFile copyToStorage(Path file, String relPath) {
        try (InputStream in = Files.newInputStream(file)) {
            return storageService.copyPreservingPath(relPath, in);
        } catch (IOException e) {
            throw new InvoiceFileImportException("Kaynak dosya okunamadı: " + file, e);
        }
    }

    /**
     * Kaynak dosyanın SHA-256 hex digestini KOPYALAMADAN hesaplar (akışı bir kez
     * {@link DigestInputStream} ile okur). İçerik-dedup kararı kopyalamadan ÖNCE bu
     * digest üzerinden verilir; böylece atlanan içerik storage köküne hiç yazılmaz.
     */
    private String computeSha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream in = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                while (dis.read(buffer) != -1) {
                    // akışı tüket; digest güncellenir
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her JRE'de mevcuttur; teorik durum.
            throw new InvoiceFileImportException("SHA-256 algoritması yok", e);
        } catch (IOException e) {
            throw new InvoiceFileImportException("Kaynak dosya okunamadı (hash): " + file, e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------
    // Eşleme
    // ------------------------------------------------------------------------

    private Invoice findMatch(String relPath,
                              Map<String, Invoice> noteIndex,
                              Map<String, Invoice> baseIndex) {
        // (1) Tam note-path eşleşmesi
        Invoice exact = noteIndex.get(relPath);
        if (exact != null) {
            return exact;
        }
        // (2) Türev/kardeş eşleşmesi: aynı klasör + aynı taban ad
        String baseKey = folderBaseKey(relPath);
        if (baseKey != null) {
            return baseIndex.get(baseKey);
        }
        return null;
    }

    private boolean isUnderTrash(String relPath) {
        return relPath.equals(TRASH_DIR) || relPath.startsWith(TRASH_DIR + "/");
    }

    // ------------------------------------------------------------------------
    // Path / tip yardımcıları (paket-private: birim testi için)
    // ------------------------------------------------------------------------

    /**
     * invoice.note değerini storage-root göreli yola normalize eder. Path değilse
     * (serbest açıklama) {@code null} döner. Baştaki {@code faturalar/} öneki atılır,
     * ters bölü düzleştirilir.
     */
    static String normalizeNotePath(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim().replace('\\', '/');
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(NOTE_PATH_PREFIX)) {
            trimmed = trimmed.substring(NOTE_PATH_PREFIX.length());
        }
        // Bir uzantı içermiyorsa muhtemelen path değil (ör. serbest açıklama) → atla.
        int lastSlash = trimmed.lastIndexOf('/');
        String last = lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
        if (!last.contains(".") || last.contains(" ")) {
            return null;
        }
        return trimmed;
    }

    /**
     * {@code <klasör>/<taban>} anahtarı üretir. Taban ad, dosya adından uzantı ve
     * bilinen tip ekleri ({@code _statement}, {@code _receipt}) atılarak elde edilir.
     * Böylece {@code 2026-03/aws_mart.pdf}, {@code 2026-03/aws_mart_statement.pdf} ve
     * {@code 2026-03/aws_mart.xml} hepsi {@code 2026-03/aws_mart} anahtarına gider.
     * Klasörü olmayan (kök seviyesi) dosyalar için {@code null} döner (kardeşlik kurulmaz).
     */
    static String folderBaseKey(String relPath) {
        String norm = relPath.replace('\\', '/');
        int lastSlash = norm.lastIndexOf('/');
        if (lastSlash < 0) {
            return null; // ay klasörü yok → kardeşlik belirsiz
        }
        String folder = norm.substring(0, lastSlash);
        String name = norm.substring(lastSlash + 1);
        String base = baseName(name);
        return base.isEmpty() ? null : folder + "/" + base;
    }

    /** Dosya adından uzantı + bilinen tip eklerini atıp taban adı döndürür. */
    static String baseName(String fileName) {
        String name = fileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : DERIVED_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    /** Dosya adı/uzantıdan {@link FileType} türetir. */
    static FileType detectFileType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xml")) {
            return FileType.XML;
        }
        if (lower.contains("statement")) {
            return FileType.STATEMENT;
        }
        if (lower.contains("refund") || lower.contains("receipt")) {
            return FileType.RECEIPT;
        }
        if (lower.endsWith(".pdf")) {
            return FileType.PDF;
        }
        return FileType.OTHER;
    }

    private String probeMime(Path file, FileType fileType) {
        try {
            String probed = Files.probeContentType(file);
            if (probed != null && !probed.isBlank()) {
                return probed;
            }
        } catch (IOException ignored) {
            // best-effort
        }
        return switch (fileType) {
            case PDF, STATEMENT, RECEIPT -> "application/pdf";
            case XML -> "application/xml";
            default -> "application/octet-stream";
        };
    }
}
