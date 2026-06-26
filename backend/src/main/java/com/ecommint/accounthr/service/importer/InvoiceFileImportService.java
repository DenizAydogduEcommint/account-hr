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

    /**
     * Taban ad türetirken atılan tip ekleri (note dosyasıyla kardeşlik kurmak için).
     * {@code _refund}: CLAUDE.md, {@code aws_mart_refund.pdf}'i ana faturanın ({@code aws_mart})
     * kardeşi sayar → türev taban {@code aws_mart} olur.
     */
    private static final String[] DERIVED_SUFFIXES = {"_statement", "_receipt", "_refund"};

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
        // Aynı baseKey'e düşen birden fazla note çakışmasını çöz: note'u TAM taban dosya adı
        // olan invoice tercih edilir (türev _statement/_receipt/_refund formu DEĞİL). Çakışma
        // hâlâ belirsizse (iki "tam taban" ya da iki türev) baseKey indeksten DÜŞÜRÜLÜR →
        // kardeş dosya yanlış invoice'a bağlanmaz, unmatched'e gider (rapor + uyarı logu).
        Set<String> ambiguousBaseKeys = new HashSet<>();
        Map<String, Boolean> baseKeyHasExact = new HashMap<>();
        for (Invoice invoice : invoiceRepository.findByNoteIsNotNull()) {
            String rel = normalizeNotePath(invoice.getNote());
            if (rel == null) {
                continue; // path değil, serbest açıklama → atla
            }
            noteIndex.putIfAbsent(rel, invoice);
            String baseKey = folderBaseKey(rel);
            if (baseKey == null) {
                continue;
            }
            boolean isExact = isExactBaseNote(rel);
            Boolean existingExact = baseKeyHasExact.get(baseKey);
            if (existingExact == null) {
                // İlk kez görülüyor → kaydet.
                baseIndex.put(baseKey, invoice);
                baseKeyHasExact.put(baseKey, isExact);
            } else if (isExact && !existingExact) {
                // Yeni gelen TAM taban, önceki türevdi → tam taban kazanır (belirsizlik değil).
                baseIndex.put(baseKey, invoice);
                baseKeyHasExact.put(baseKey, true);
            } else if (isExact == existingExact) {
                // İkisi de aynı sınıf (iki tam taban veya iki türev) → belirsiz.
                ambiguousBaseKeys.add(baseKey);
            }
            // (isExact==false && existingExact==true): mevcut tam tabanı koru → değişme yok.
        }
        for (String key : ambiguousBaseKeys) {
            baseIndex.remove(key);
            log.warn("baseKey belirsiz (birden çok note aynı taban ada düşüyor) → kardeş "
                    + "eşleştirme devre dışı, dosyalar unmatched'e gidecek: {}", key);
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
        // Bu run'da bir sha içeriğine bağlanan invoice id'leri (null = eşleşmedi/trash).
        // E2-DR-1: aynı içerik bu run'da FARKLI bir faturaya eşleşirse o faturaya AYRI bir
        // FileAsset satırıyla bağlanır (sessiz düşürme/unmatched değil); yalnızca AYNI
        // (fatura, sha) ikilisi gerçek-duplicate sayılır ve atlanır.
        Map<String, Set<Long>> shaBoundInvoicesThisRun = new HashMap<>();

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
            boolean physicallyPresent = alreadyInDb || sameRunDuplicate; // fizik dosya zaten yazıldı

            // --- Eşleme (her durumda lazım: bağlanacak fatura belirlenir) ---
            boolean inTrash = isUnderTrash(relPath);
            Invoice match = inTrash ? null : findMatch(relPath, noteIndex, baseIndex);
            Long thisInvoiceId = invoiceIdOf(match);

            // --- Duplicate tespiti (raporlama amaçlı) ---
            // ÖNEMLİ (E2-DR-1): "duplicate" sayacı SADECE gerçek atlamada (genuine skip) artar.
            // alreadyInDb/sameRunDuplicate, içeriğin fiziken mevcut olduğunu söyler ama meşru bir
            // ÇAPRAZ-FATURA paylaşımı da bu duruma düşer (aynı bytes, FARKLI fatura → yeni satır,
            // duplicate DEĞİL). Bu yüzden global sha mevcudiyetiyle sayma; yalnızca aynı (fatura,
            // sha) ikilisi zaten varsa (continue ile atlanan satır) duplicate say. Dosya adında
            // "duplicate" geçen kayıtlar (operatör işaretli) her zaman duplicate sayılır.
            boolean nameMarkedDuplicate = fileName.toLowerCase(Locale.ROOT).contains("duplicate");

            if (physicallyPresent) {
                // E2-DR-1: Bu içeriğin fizik kopyası zaten storage'da var. İki olasılık:
                //   (a) Gerçek-duplicate — AYNI (fatura, sha) zaten kayıtlı → atla (yeni satır YOK).
                //       Re-run'da her dosya buraya düşer → 0 yeni satır, 0 kopya (idempotent).
                //   (b) Çapraz-fatura paylaşımı — aynı içerik FARKLI bir faturaya eşleşiyor →
                //       o faturaya AYRI bir FileAsset satırı oluştur, mevcut fizik path'i YENİDEN
                //       KULLAN (bytes tekrar kopyalanmaz; dosya bir kez saklanır).
                if (sameInvoiceShaExists(sha256, thisInvoiceId, shaBoundInvoicesThisRun)) {
                    duplicates++; // (a) GERÇEK skip → tek duplicate burada sayılır
                    continue; // (a) gerçek-duplicate → atla
                }
                // (b) Çapraz-fatura paylaşımı: meşru yeni satır; ad işaretliyse yine duplicate say.
                if (nameMarkedDuplicate) {
                    duplicates++;
                }
                // (b) Farklı faturaya bağla: mevcut bir FileAsset'in fizik metadatasını yeniden kullan.
                FileAsset source0 = existingAssetForSha(sha256);
                if (source0 == null) {
                    // Savunma: physicallyPresent ama referans satır bulunamadı (beklenmez).
                    log.warn("İçerik fizik olarak mevcut sayıldı ama referans FileAsset yok: {}", relPath);
                    continue;
                }
                FileAsset attach = new FileAsset();
                attach.setInvoice(match);
                attach.setFilePath(source0.getFilePath());
                attach.setFileName(source0.getFileName());
                attach.setFileType(source0.getFileType());
                attach.setMimeType(source0.getMimeType());
                attach.setSizeBytes(source0.getSizeBytes());
                attach.setSha256(sha256);
                fileAssetRepository.save(attach);
                newFileRows++; // gerçek yeni satır (ama fizik kopya YOK → copied artmaz)
                rememberShaInvoice(shaBoundInvoicesThisRun, sha256, thisInvoiceId);

                if (inTrash) {
                    trashed++;
                    unmatched++;
                    unmatchedFiles.add(relPath);
                } else if (match != null) {
                    matched++; // E2-DR-1: içerik-paylaşımlı dosya artık BAĞLANDI
                } else {
                    unmatched++;
                    unmatchedFiles.add(relPath);
                }
                continue;
            }
            shaSeenThisRun.add(sha256);

            // Yeni içerik (ilk kez görülüyor). Atlama YOK → duplicate değil; ancak operatör
            // dosya adına "duplicate" işaretlediyse raporda yine duplicate sayılır.
            if (nameMarkedDuplicate) {
                duplicates++;
            }

            // --- Yeni içerik → şimdi (ve yalnızca şimdi) fiziken kopyala ---
            StoredFile stored = copyToStorage(file, relPath);
            copied++; // DB'de yoktu → fiilen (ilk kez) kopyalandı
            rememberShaInvoice(shaBoundInvoicesThisRun, sha256, thisInvoiceId);

            FileAsset asset = new FileAsset();
            asset.setInvoice(match);
            asset.setFilePath(stored.relativePath());
            asset.setFileName(stored.fileName());
            asset.setFileType(fileType);
            asset.setMimeType(probeMime(file, fileType));
            asset.setSizeBytes(stored.sizeBytes());
            // DB sha, depolanan dosyanın (copyPreservingPath'in yazdığı bytes) hash'i olmalı —
            // ilk okumanın hash'i değil. İkisi normalde aynıdır; stored.sha256() kanonik kaynaktır.
            asset.setSha256(stored.sha256());
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

    private static Long invoiceIdOf(Invoice invoice) {
        return invoice != null ? invoice.getId() : null;
    }

    /**
     * E2-DR-1: AYNI (fatura, sha) ikilisi zaten var mı? (gerçek-duplicate kontrolü)
     * Dolu invoiceId için DB bileşik tekiliyle aynı tanecik: {@code existsByInvoiceIdAndSha256}.
     * {@code invoiceId == null} (eşleşmemiş/trash) için Spring Data {@code invoice_id = null}
     * üretir ve hiçbir satırla eşleşmez; bu yüzden null-invoice content-dedup açıkça ele alınır:
     * mevcut bir FileAsset (DB) ya da bu run'da bağlanmış bir null-invoice sahibi varsa
     * gerçek-duplicate sayılır (ikinci null-invoice satırı oluşturulmaz).
     */
    private boolean sameInvoiceShaExists(String sha256, Long invoiceId,
                                         Map<String, Set<Long>> shaBoundInvoicesThisRun) {
        if (invoiceId != null) {
            if (fileAssetRepository.existsByInvoiceIdAndSha256(invoiceId, sha256)) {
                return true;
            }
            Set<Long> bound = shaBoundInvoicesThisRun.get(sha256);
            return bound != null && bound.contains(invoiceId);
        }
        // null-invoice: bu sha için null sahipli bir satır zaten var mı?
        for (FileAsset owner : fileAssetRepository.findBySha256(sha256)) {
            if (owner.getInvoice() == null) {
                return true;
            }
        }
        Set<Long> bound = shaBoundInvoicesThisRun.get(sha256);
        return bound != null && bound.contains(null);
    }

    /**
     * E2-DR-1: Bu sha içeriğine sahip mevcut bir FileAsset (fizik metadata kaynağı) bulur.
     * Çapraz-fatura paylaşımında yeni satır, var olan satırın fizik path'ini/adını yeniden
     * kullanır (bytes tekrar kopyalanmaz). Önce DB'ye bakar (re-run/önceki run); yoksa bu run'da
     * yazılmış bir satır vardır → DB flush'lanmış olacağından genelde DB yeterlidir.
     */
    private FileAsset existingAssetForSha(String sha256) {
        List<FileAsset> existing = fileAssetRepository.findBySha256(sha256);
        return existing.isEmpty() ? null : existing.get(0);
    }

    private static void rememberShaInvoice(Map<String, Set<Long>> map, String sha256, Long invoiceId) {
        map.computeIfAbsent(sha256, k -> new HashSet<>()).add(invoiceId);
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

    /**
     * Bu note göreli yolu TAM taban dosya adına mı işaret ediyor (türev DEĞİL)?
     * Yani uzantı atıldıktan sonra adı bilinen bir tip ekiyle ({@code _statement},
     * {@code _receipt}, {@code _refund}) BİTMİYORsa true. Çakışan baseKey'lerde tam
     * tabanlı note türev formlara tercih edilir.
     */
    static boolean isExactBaseNote(String relPath) {
        String norm = relPath.replace('\\', '/');
        int lastSlash = norm.lastIndexOf('/');
        String name = lastSlash >= 0 ? norm.substring(lastSlash + 1) : norm;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : DERIVED_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return false;
            }
        }
        return true;
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
