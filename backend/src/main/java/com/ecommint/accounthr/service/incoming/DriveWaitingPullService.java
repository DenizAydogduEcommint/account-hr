package com.ecommint.accounthr.service.incoming;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommint.accounthr.config.DriveSyncProperties;
import com.ecommint.accounthr.config.StorageProperties;
import com.ecommint.accounthr.dto.incoming.IncomingInvoiceResponse;
import com.ecommint.accounthr.dto.incoming.IncomingPullResult;

/**
 * E5-02 — Drive {@code faturalar/waiting/} PULL + ham fatura (incoming invoice) INGEST servisi.
 *
 * <h2>Yalnızca COPY (pull) + KAYIT</h2>
 * Bu round: Drive waiting/ → lokal landing dizini (rclone {@code copy}, {@link RcloneClient}),
 * ardından her dosya için bir {@link IncomingInvoice} (status {@link IncomingStatus#NEW}). Ay
 * klasörüne taşıma ve waiting'den silme E5-04 (eşleştirme) ile gelecek; <b>burada Drive'a hiçbir
 * push/delete YOKTUR</b> — {@link RcloneClient}'ın yapısal olarak tek yeteneği pull'dur.
 *
 * <h2>Idempotency</h2>
 * Bir dosya YENİ sayılır ancak: (a) {@code (DRIVE_WAITING, sourceRef)} kombinasyonu DB'de yoksa
 * <b>VE</b> (b) aynı {@code sha256} DB'de yoksa. Aksi halde dosya atlanır (skipped). sourceRef =
 * landing dizinine göreli dosya yolu (Drive waiting yapısını korur).
 *
 * <h2>Güvenlik</h2>
 * Landing dizini STORAGE_ROOT altındadır ({@code incoming/_landing}) ve invoice/statement
 * store'larından ayrıdır. {@code expenses/faturalar} (Drive aynası) ASLA okunmaz/yazılmaz.
 */
@Service
public class DriveWaitingPullService {

    private static final Logger log = LoggerFactory.getLogger(DriveWaitingPullService.class);

    /** STORAGE_ROOT altında rclone'un pull'ladığı dosyaların indiği landing alt-dizini. */
    static final String LANDING_SUBDIR = "incoming/_landing";

    private final RcloneClient rcloneClient;
    private final DriveSyncProperties driveProps;
    private final StorageProperties storageProps;
    private final IncomingInvoiceService incomingInvoiceService;

    public DriveWaitingPullService(RcloneClient rcloneClient,
                                   DriveSyncProperties driveProps,
                                   StorageProperties storageProps,
                                   IncomingInvoiceService incomingInvoiceService) {
        this.rcloneClient = rcloneClient;
        this.driveProps = driveProps;
        this.storageProps = storageProps;
        this.incomingInvoiceService = incomingInvoiceService;
    }

    /**
     * Drive {@code waiting/}'i lokale çeker ve YENİ dosyalar için ham fatura kaydı oluşturur.
     *
     * <h3>Tx sınırı</h3>
     * Bu metot {@code @Transactional} DEĞİLDİR. Her dosyanın persist'i
     * {@link IncomingInvoiceService#ingestOne} ile KENDİ tx'inde (REQUIRES_NEW) yapılır;
     * böylece eşzamanlı çift pull'da bir dosyanın unique-index çakışması yalnızca o dosyayı
     * atlatır, batch'i geri almaz (önceki bulgu: dış tek tx + yakalanan çakışma TÜM batch'i
     * rollback-only yapıp 409 dönüyordu).
     *
     * @return pull + ingest özeti (pulled / new / skipped + yeni satırlar)
     * @throws RcloneException rclone pull başarısızlığında (502'ye çevrilir)
     */
    public IncomingPullResult pull() {
        Path landing = resolveLandingDir();

        // Fix: Landing geçici staging'dir; önceki pull'ların dosyaları rclone copy ile burada
        // kalır → her pull eski dosyaları yeniden tarar (pulledCount şişer; aynı-isimli içeriği
        // değişmiş bir dosya sonsuza dek atlanır). Her pull'un BAŞINDA landing'i temizle (yalnız
        // içerik; dizini koru). Kanonik store incoming/<sha256>/<file>; sha256 gerçek mükerrerleri
        // hâlâ eler. GÜVENLİK: yalnızca STORAGE_ROOT altındaki landing'e dokunulur.
        clearLanding(landing);

        // remote → local PULL (yalnızca copy; push/delete YOK).
        rcloneClient.copyToLocal(remoteWaitingPath(), landing);

        List<Path> files = listFilesRecursive(landing);
        int pulled = files.size();
        int newCount = 0;
        int skipped = 0;
        List<IncomingInvoiceResponse> created = new ArrayList<>();

        for (Path file : files) {
            String sourceRef = landing.relativize(file).toString();
            String fileName = file.getFileName().toString();
            String sha256 = sha256Of(file);

            // Her dosya KENDİ tx'inde persist edilir (REQUIRES_NEW). Mükerrer (önden kontrol)
            // VEYA eşzamanlı yarış (unique-index çakışması) → Optional.empty() = atla. Bir
            // dosyanın çakışması diğer dosyaları ETKİLEMEZ; batch devam eder, 409 dönmez.
            Optional<IncomingInvoiceResponse> result =
                    incomingInvoiceService.ingestOne(file, sourceRef, fileName, sha256);
            if (result.isPresent()) {
                created.add(result.get());
                newCount++;
            } else {
                skipped++;
                log.debug("Ham fatura atlandı (mükerrer/çakışma): sourceRef={} sha256={}",
                        sourceRef, sha256);
            }
        }

        String message = String.format(
                "Pull tamam: %d dosya tarandı, %d yeni ham fatura, %d mükerrer atlandı.",
                pulled, newCount, skipped);
        log.info(message);
        return new IncomingPullResult(pulled, newCount, skipped, created, message);
    }

    /** rclone remote waiting yolu: {@code <remote>:<waitingRemotePath>}. */
    private String remoteWaitingPath() {
        return driveProps.getRemoteName() + ":" + driveProps.getWaitingRemotePath();
    }

    /** Landing dizinini STORAGE_ROOT altında çözer. */
    Path resolveLandingDir() {
        String root = storageProps.getRoot();
        if (root == null || root.isBlank()) {
            throw new RcloneException("Landing dizini çözülemedi: app.storage.root boş.");
        }
        return Paths.get(root).resolve(LANDING_SUBDIR).toAbsolutePath().normalize();
    }

    /**
     * Landing dizininin İÇERİĞİNİ siler ve dizini (yeniden) oluşturur. Yalnızca STORAGE_ROOT
     * altındaki landing'e dokunur — {@code expenses/faturalar} (Drive aynası) veya başka bir
     * yere ASLA. Dizinin kendisini korur (yalnızca alt dosya/dizinleri siler).
     */
    void clearLanding(Path landing) {
        // Güvenlik bekçisi: landing gerçekten STORAGE_ROOT altındaki beklenen alt-dizin mi?
        Path expected = resolveLandingDir();
        if (!landing.equals(expected)) {
            throw new RcloneException("Landing temizlenemedi: beklenmeyen yol " + landing);
        }
        try {
            if (Files.isDirectory(landing)) {
                try (Stream<Path> stream = Files.walk(landing)) {
                    stream.sorted(Comparator.reverseOrder())
                            .filter(p -> !p.equals(landing))
                            .forEach(p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                }
            }
            Files.createDirectories(landing);
        } catch (IOException | UncheckedIOException e) {
            throw new RcloneException("Landing dizini temizlenemedi: " + landing,
                    e instanceof UncheckedIOException u ? u.getCause() : (IOException) e);
        }
    }

    /** Dizindeki tüm düz dosyaları (alt-dizinler dahil) deterministik sırada listeler. */
    private List<Path> listFilesRecursive(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new RcloneException("Landing dizini taranamadı: " + dir, e);
        }
    }

    /** Bir dosyanın SHA-256 hex digestini hesaplar. */
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
            throw new RcloneException("Landing dosyasının hash'i hesaplanamadı: " + file, e);
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
}
