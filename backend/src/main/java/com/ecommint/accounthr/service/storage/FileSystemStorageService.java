package com.ecommint.accounthr.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.config.StorageProperties;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.repository.FileAssetRepository;

import jakarta.annotation.PostConstruct;

/**
 * {@link StorageService}'in lokal dosya-sistemi implementasyonu (E1-04).
 *
 * <p>Yalnızca {@code app.storage.root} (STORAGE_ROOT) altında çalışır. Drive aynası
 * {@code expenses/faturalar}'a ASLA referans vermez/dokunmaz. Veri migrasyonu
 * (kopyalama) bu sınıfın işi değildir (E2-03).
 */
@Service
public class FileSystemStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemStorageService.class);

    static final String WAITING_DIR = "waiting";
    static final String TRASH_DIR = "trash";

    /** Türkçe ay adları (1=ocak ... 12=aralik), slugify edilmiş (boşluksuz, ascii). */
    private static final String[] TURKISH_MONTHS = {
            "ocak", "subat", "mart", "nisan", "mayis", "haziran",
            "temmuz", "agustos", "eylul", "ekim", "kasim", "aralik"
    };

    private final StorageProperties properties;
    private final FileAssetRepository fileAssetRepository;

    private Path root;

    public FileSystemStorageService(StorageProperties properties,
                                    FileAssetRepository fileAssetRepository) {
        this.properties = properties;
        this.fileAssetRepository = fileAssetRepository;
    }

    /**
     * Başlangıçta kök + waiting/ + trash/ dizinlerini oluşturur (yoksa). Kök boş başlar.
     */
    @PostConstruct
    void init() {
        if (properties.getRoot() == null || properties.getRoot().isBlank()) {
            throw new StorageException("app.storage.root (STORAGE_ROOT) tanımlı değil.");
        }
        this.root = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve(WAITING_DIR));
            Files.createDirectories(root.resolve(TRASH_DIR));
        } catch (IOException e) {
            throw new StorageIOException("Storage kök dizinleri oluşturulamadı: " + root, e);
        }
        log.info("Fatura storage kökü hazır: {}", root);
    }

    @Override
    public StoredFile store(Long invoiceId,
                            LocalDate invoiceDate,
                            String serviceName,
                            Long providerId,
                            String invoiceNo,
                            String originalFilename,
                            InputStream content,
                            FileType fileType) {
        if (invoiceDate == null) {
            throw new StorageException("invoiceDate zorunludur (klasör/ay adı bundan türetilir).");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new StorageException("serviceName zorunludur.");
        }

        // --- Mantıksal duplicate: aynı (provider, invoice_no) zaten depolanmışsa ---
        // İş kuralı: Invoice + Receipt aynı işlem için gelirse Invoice tutulur. Burada
        // ilk gelen kayıt korunur ve ikincisi reddedilir; "Invoice'ı Receipt'e tercih
        // et" gibi ince ayar daha sonra iş katmanında çözülecektir.
        if (providerId != null && invoiceNo != null && !invoiceNo.isBlank()) {
            List<FileAsset> existing =
                    fileAssetRepository.findByInvoiceProviderIdAndInvoiceInvoiceNo(providerId, invoiceNo);
            if (!existing.isEmpty()) {
                throw new DuplicateFileException(
                        "Aynı (provider=" + providerId + ", invoiceNo=" + invoiceNo
                                + ") için zaten bir dosya mevcut.");
            }
        }

        // --- Ay klasörü: fatura tarihinden YYYY-MM (ödeme tarihinden DEĞİL) ---
        String monthFolder = String.format("%04d-%02d", invoiceDate.getYear(), invoiceDate.getMonthValue());
        Path targetDir = resolveUnderRoot(monthFolder);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new StorageIOException("Ay klasörü oluşturulamadı: " + targetDir, e);
        }

        // --- İçeriği geçici dosyaya yaz + SHA-256 hesapla ---
        String extension = resolveExtension(originalFilename, fileType);
        Path tempFile;
        String sha256;
        long size;
        try {
            tempFile = Files.createTempFile(targetDir, ".upload-", ".tmp");
        } catch (IOException e) {
            throw new StorageIOException("Geçici dosya oluşturulamadı: " + targetDir, e);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(content, digest)) {
                size = Files.copy(dis, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            sha256 = toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            deleteQuietly(tempFile);
            throw new StorageException("SHA-256 algoritması bulunamadı.", e);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new StorageIOException("Dosya yazılamadı: " + targetDir, e);
        }

        // --- İçerik bazlı duplicate (E2-DR-1): FATURA başına. AYNI faturada aynı SHA-256
        //     zaten varsa reddet; FARKLI faturaya aynı içerik artık serbest (byte-aynı dosya
        //     iki meşru faturaya ait olabilir; fizik dosya yine de paylaşılarak bir kez saklanır,
        //     ikinci satır importer üzerinden aynı path'i referanslar). ---
        if (invoiceId != null && fileAssetRepository.existsByInvoiceIdAndSha256(invoiceId, sha256)) {
            deleteQuietly(tempFile);
            throw new DuplicateFileException("Aynı içeriğe (SHA-256=" + sha256 + ") sahip dosya zaten mevcut.");
        }

        // --- Çakışmasız nihai dosya adı (slug + ay + tip eki [+ _1, _2 ...]) ---
        String baseName = buildBaseName(serviceName, invoiceDate, fileType);
        Path finalPath = resolveNonClashingName(targetDir, baseName, extension);

        // Atomik taşı. ATOMIC_MOVE başarısız olursa, eşzamanlı bir yazar tam o anda aynı
        // hedef adı oluşturmuş olabilir; düz Files.move ile FARKLI bir dosyayı SESSİZCE
        // EZMEK yerine, taze çakışmasız bir ad yeniden çözülür ve ATOMIC_MOVE tekrar
        // denenir. Yalnızca ikinci deneme de başarısız olursa hata fırlatılır. Kısmi/
        // başarısız taşımada temp dosyasının orphan kalmamasını moved bayrağı + finally
        // garanti eder.
        boolean moved = false;
        try {
            try {
                Files.move(tempFile, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                // Eşzamanlı yazar hedefi kapmış olabilir → yeni boş ad bul, tekrar dene.
                finalPath = resolveNonClashingName(targetDir, baseName, extension);
                Files.move(tempFile, finalPath, StandardCopyOption.ATOMIC_MOVE);
            }
            moved = true;
        } catch (IOException e) {
            throw new StorageIOException("Dosya taşınamadı: " + finalPath, e);
        } finally {
            if (!moved) {
                deleteQuietly(tempFile);
            }
        }

        String relativePath = root.relativize(finalPath).toString();
        log.info("Fatura dosyası depolandı invoiceId={} path={} sha256={} size={}",
                invoiceId, relativePath, sha256, size);
        return new StoredFile(relativePath, finalPath.getFileName().toString(), sha256, size);
    }

    @Override
    public StoredFile copyPreservingPath(String relativePath, InputStream content) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new StorageException("relativePath zorunludur.");
        }
        // Kök altında çöz + yol-aşımı doğrula (savunma: kaynak adı '..' içeremez).
        Path target = resolveUnderRoot(relativePath);
        if (target.equals(root)) {
            throw new StoragePathTraversalException("Hedef yol kökün kendisi olamaz: " + relativePath);
        }
        Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new StorageIOException("Hedef dizin oluşturulamadı: " + parent, e);
        }

        // İçeriği geçici dosyaya yaz + SHA-256 hesapla.
        Path tempFile;
        try {
            tempFile = Files.createTempFile(parent, ".copy-", ".tmp");
        } catch (IOException e) {
            throw new StorageIOException("Geçici dosya oluşturulamadı: " + parent, e);
        }
        String sha256;
        long size;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(content, digest)) {
                size = Files.copy(dis, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            sha256 = toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            deleteQuietly(tempFile);
            throw new StorageException("SHA-256 algoritması bulunamadı.", e);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new StorageIOException("Dosya yazılamadı: " + target, e);
        }

        String relForReturn = root.relativize(target).toString();
        String fileName = target.getFileName().toString();

        // Idempotent: hedef zaten varsa ve aynı içerikse (SHA-256) yeniden kopyalama.
        // sha256Of fırlatsa bile temp dosyası finally ile temizlenir (orphan bırakma).
        if (Files.exists(target)) {
            try {
                String existingSha = sha256Of(target);
                if (sha256.equals(existingSha)) {
                    log.debug("copyPreservingPath atlandı (aynı içerik mevcut): {}", relForReturn);
                    return new StoredFile(relForReturn, fileName, sha256, size);
                }
                // Farklı içerik aynı yolda → migrasyonda olmamalı; üzerine yazmayı reddet.
                throw new StorageException(
                        "Hedefte farklı içerikli bir dosya zaten var (üzerine yazılmaz): " + relForReturn);
            } finally {
                deleteQuietly(tempFile);
            }
        }

        // Atomik taşı; başarısız olursa normal taşımaya düş. Kısmi/başarısız taşımada
        // temp dosyasının orphan kalmamasını moved bayrağı + finally garanti eder.
        boolean moved = false;
        try {
            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tempFile, target);
            }
            moved = true;
        } catch (IOException e) {
            throw new StorageIOException("Dosya taşınamadı: " + target, e);
        } finally {
            if (!moved) {
                deleteQuietly(tempFile);
            }
        }

        log.info("Migrasyon kopyası yazıldı path={} sha256={} size={}", relForReturn, sha256, size);
        return new StoredFile(relForReturn, fileName, sha256, size);
    }

    /** Var olan bir dosyanın SHA-256 hex digestini hesaplar (idempotency kontrolü). */
    private String sha256Of(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) {
                    // digest beslenir
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new StorageIOException("Mevcut dosyanın hash'i hesaplanamadı: " + file, e);
        }
    }

    @Override
    @Transactional
    public void moveToWaiting(Long fileId) {
        relocate(fileId, WAITING_DIR);
    }

    @Override
    @Transactional
    public void moveToTrash(Long fileId) {
        relocate(fileId, TRASH_DIR);
    }

    @Override
    public Resource loadAsResource(Long fileId) {
        FileAsset asset = fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new StorageException("Dosya kaydı bulunamadı: id=" + fileId));
        Path file = resolveUnderRoot(asset.getFilePath());
        if (!Files.exists(file) || !Files.isReadable(file)) {
            throw new StorageException("Fiziksel dosya okunamıyor: " + asset.getFilePath());
        }
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new StorageException("Kaynak okunamıyor: " + asset.getFilePath());
            }
            return resource;
        } catch (IOException e) {
            throw new StorageIOException("Kaynak yüklenemedi: " + asset.getFilePath(), e);
        }
    }

    @Override
    public boolean deletePhysical(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        Path file;
        try {
            file = resolveUnderRoot(relativePath);
        } catch (StoragePathTraversalException e) {
            log.warn("Orphan silme atlandı (kök dışı yol): {}", relativePath);
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Orphan fiziksel dosya silindi: {}", relativePath);
            }
            return deleted;
        } catch (IOException e) {
            log.warn("Orphan fiziksel dosya silinemedi: {}", relativePath, e);
            return false;
        }
    }

    // ------------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------------

    private void relocate(Long fileId, String subDir) {
        FileAsset asset = fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new StorageException("Dosya kaydı bulunamadı: id=" + fileId));

        Path source = resolveUnderRoot(asset.getFilePath());
        Path targetDir = resolveUnderRoot(subDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new StorageIOException("Hedef dizin oluşturulamadı: " + targetDir, e);
        }

        if (!Files.exists(source)) {
            throw new StorageException("Taşınacak fiziksel dosya yok: " + asset.getFilePath());
        }

        // Hedefte çakışma olursa benzersizleştir.
        String fileName = source.getFileName().toString();
        Path target = uniqueTarget(targetDir, fileName);
        try {
            Files.move(source, target);
        } catch (IOException e) {
            throw new StorageIOException("Dosya taşınamadı: " + source + " -> " + target, e);
        }

        asset.setFilePath(root.relativize(target).toString());
        fileAssetRepository.save(asset);
        log.info("Dosya taşındı id={} -> {}", fileId, asset.getFilePath());
    }

    private Path uniqueTarget(Path dir, String fileName) {
        Path candidate = dir.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String base = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }
        for (int i = 1; ; i++) {
            Path next = dir.resolve(base + "_" + i + ext);
            if (!Files.exists(next)) {
                return next;
            }
        }
    }

    /**
     * Verilen göreli yolu kök altında çözer ve normalize edip kök sınırını doğrular.
     * {@code ..} ile dışarı çıkma denemeleri {@link StoragePathTraversalException} fırlatır.
     */
    Path resolveUnderRoot(String relative) {
        if (relative == null) {
            throw new StoragePathTraversalException("Boş yol.");
        }
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new StoragePathTraversalException(
                    "Yol storage kökünün dışına çıkıyor: " + relative);
        }
        return resolved;
    }

    /** Dosya adı için çakışma çözer: önce base.ext, sonra base_1.ext, base_2.ext ... */
    private Path resolveNonClashingName(Path dir, String baseName, String extension) {
        Path candidate = dir.resolve(baseName + extension);
        // Üretilen adın da kök altında kaldığını doğrula (savunma amaçlı).
        if (!candidate.normalize().startsWith(root)) {
            throw new StoragePathTraversalException("Üretilen dosya adı kök dışına çıkıyor: " + baseName);
        }
        if (!Files.exists(candidate)) {
            return candidate;
        }
        for (int i = 1; ; i++) {
            Path next = dir.resolve(baseName + "_" + i + extension);
            if (!Files.exists(next)) {
                return next;
            }
        }
    }

    /** {slug}_{tr-ay}[_statement] gövdesini üretir (uzantı ayrı eklenir). */
    private String buildBaseName(String serviceName, LocalDate invoiceDate, FileType fileType) {
        String slug = slugify(serviceName);
        String month = TURKISH_MONTHS[invoiceDate.getMonthValue() - 1];
        String name = slug + "_" + month;
        if (fileType == FileType.STATEMENT) {
            name += "_statement";
        } else if (fileType == FileType.RECEIPT) {
            name += "_receipt";
        }
        return name;
    }

    /**
     * Türkçe-duyarlı slugify: ç→c, ş→s, ğ→g, ı→i, İ→i, ö→o, ü→u; küçük harf;
     * boşluklar tek alt çizgiye; harf/rakam/_/- dışındaki karakterler atılır.
     * Örn: "Google Workspace" → "google_workspace", "İş Bankası" → "is_bankasi".
     */
    static String slugify(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case 'ç', 'Ç' -> sb.append('c');
                case 'ş', 'Ş' -> sb.append('s');
                case 'ğ', 'Ğ' -> sb.append('g');
                case 'ı', 'I' -> sb.append('i');
                case 'İ', 'i' -> sb.append('i');
                case 'ö', 'Ö' -> sb.append('o');
                case 'ü', 'Ü' -> sb.append('u');
                case ' ', '\t', '\n', '_' -> sb.append('_');
                default -> {
                    char lower = Character.toLowerCase(c);
                    if ((lower >= 'a' && lower <= 'z') || (lower >= '0' && lower <= '9') || lower == '-') {
                        sb.append(lower);
                    }
                    // diğer her şey (nokta, /, \, vb.) atılır
                }
            }
        }
        // Ardışık alt çizgileri sadeleştir, baş/son alt çizgileri kırp.
        String slug = sb.toString().replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "dosya" : slug;
    }

    /**
     * Uzantıyı belirler: önce orijinal dosya adından (sanitize edilmiş), yoksa
     * dosya tipinden türetir. Asla {@code ..} / yol ayıracı içermez.
     */
    private String resolveExtension(String originalFilename, FileType fileType) {
        if (originalFilename != null) {
            String name = Paths.get(originalFilename).getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]", "");
                if (!ext.isEmpty()) {
                    return "." + ext;
                }
            }
        }
        return switch (fileType) {
            case PDF -> ".pdf";
            case XML -> ".xml";
            default -> ".bin";
        };
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // yoksay
        }
    }

    /** Test/erişim için: çözülmüş kök. */
    Path getRoot() {
        return root;
    }
}
