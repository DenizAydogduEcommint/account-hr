package com.ecommint.accounthr.service.drive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommint.accounthr.config.DriveSyncProperties;
import com.ecommint.accounthr.config.StorageProperties;
import com.ecommint.accounthr.dto.drive.DriveSyncResult;

/**
 * Google Drive {@code waiting/} senkron köprüsü (E2-06) — rclone subprocess üzerinden.
 *
 * <h2>PUSH YASAĞI (yapısal değişmez / structural invariant)</h2>
 * Bu servis Drive'a yalnızca İKİ işlem yapar:
 * <ol>
 *   <li>{@link #pullWaiting()} — Drive {@code waiting/} → lokal dizin (rclone {@code copy},
 *       yön: <b>remote → local</b>).</li>
 *   <li>{@link #deleteFromWaiting(String)} — Drive {@code waiting/}'den TEK dosya silme
 *       (rclone {@code deletefile}).</li>
 * </ol>
 * Lokal bir dosyayı Drive'a YÜKLEYEN (upload/push/copy-to-remote) HİÇBİR metot YOKTUR
 * ve eklenmemelidir. rclone argümanlarında kaynak HER ZAMAN {@code remote:...} biçimindedir
 * (pull) ya da hiç lokal-yol-kaynağı yoktur (delete). Bu, {@code CLAUDE.md} kuralı
 * "Drive'a lokal dosya push edilmez"i KODA gömer: izin verilen tek Drive yazımı
 * {@code waiting/}'den silmedir. (Bkz. {@code DriveSyncServicePushProhibitionTest}.)
 *
 * <h2>Kimlik bilgileri</h2>
 * rclone, Drive OAuth token'ını kendi {@code rclone.conf} dosyasında tutar; bu backend
 * Drive token'larını DOĞRUDAN ELLEMEZ. Sahip olduğumuz tek yapılandırma remote adı,
 * yollar ve binary'dir ({@link DriveSyncProperties}).
 *
 * <h2>Güvenli başarısızlık</h2>
 * {@code enabled=false} → no-op (runner hiç çağrılmaz). rclone yok / sıfırdan farklı çıkış
 * kodu / zaman aşımı → {@link DriveSyncException} (loglanır; ham stderr dışarı sızmaz).
 * Olmayan dosyayı silmek hata DEĞİLDİR (idempotent).
 */
@Service
public class DriveSyncService {

    private static final Logger log = LoggerFactory.getLogger(DriveSyncService.class);

    private final DriveSyncProperties props;
    private final StorageProperties storageProperties;
    private final CommandRunner commandRunner;

    public DriveSyncService(DriveSyncProperties props,
                            StorageProperties storageProperties,
                            CommandRunner commandRunner) {
        this.props = props;
        this.storageProperties = storageProperties;
        this.commandRunner = commandRunner;
    }

    /**
     * Drive {@code waiting/}'i lokal waiting dizinine çeker (rclone {@code copy}) ve bu
     * çağrıda YENİ inen dosyaları döndürür.
     *
     * <p>Yeni-dosya tespiti: kopyadan ÖNCE ve SONRA lokal dizin listelenir; farktaki
     * dosyalar "yeni" sayılır. (rclone {@code copy} zaten yalnızca değişenleri indirir;
     * bu sayım hangi dosyaların işleme alınacağını belirler.)
     *
     * @return {@link DriveSyncResult}; köprü kapalıysa {@code skipped=true} no-op.
     * @throws DriveSyncException rclone başarısız/yok/zaman aşımı veya lokal I/O hatası.
     */
    public DriveSyncResult pullWaiting() {
        if (!props.isEnabled()) {
            log.info("Drive sync köprüsü kapalı (app.drive.enabled=false) — pullWaiting no-op.");
            return DriveSyncResult.skipped("Drive sync köprüsü kapalı (app.drive.enabled=false).");
        }

        Path localWaiting = resolveLocalWaitingDir();
        try {
            Files.createDirectories(localWaiting);
        } catch (IOException e) {
            log.error("Lokal waiting dizini oluşturulamadı: {}", localWaiting, e);
            throw new DriveSyncException("Lokal waiting dizini hazırlanamadı.", e);
        }

        Set<String> before = listFileNames(localWaiting);

        List<String> args = buildPullArgs(localWaiting);
        CommandResult result = runRclone(args, "pull");
        if (!result.isSuccess()) {
            // Ham stderr yalnızca log'a; istisna mesajı sade.
            log.error("rclone copy (pull) sıfırdan farklı çıkış kodu={} stderr={}",
                    result.exitCode(), result.stderr());
            throw new DriveSyncException(
                    "Drive waiting pull başarısız (rclone exit=" + result.exitCode() + ").");
        }

        Set<String> after = listFileNames(localWaiting);
        List<String> newFiles = new ArrayList<>();
        for (String name : after) {
            if (!before.contains(name)) {
                newFiles.add(name);
            }
        }
        newFiles.sort(String::compareTo);

        log.info("Drive waiting pull tamam: yeni dosya={} (toplam lokal={}) dizin={}",
                newFiles.size(), after.size(), localWaiting);
        return new DriveSyncResult(newFiles.size(), List.copyOf(newFiles), false,
                newFiles.size() + " yeni dosya çekildi.");
    }

    /**
     * Drive {@code waiting/}'den TEK bir dosyayı siler (rclone {@code deletefile}).
     * İşlenip ay klasörüne taşınan dosya için çağrılır.
     *
     * <p>Idempotent: olmayan dosyayı silmek HATA değildir (loglanır, sessizce geçilir).
     * Yol güvenliği: {@code fileName} bir yol değil, düz dosya adı olmalıdır — {@code /},
     * {@code \\} veya {@code ..} içeren adlar reddedilir (traversal'i imkânsızlaştırır;
     * waiting kökü dışına silme engellenir).
     *
     * @param fileName silinecek dosyanın düz adı (alt-dizin/yol İÇERMEZ)
     * @throws DriveSyncException köprü kapalı, geçersiz ad veya rclone başarısızlığı
     */
    public void deleteFromWaiting(String fileName) {
        // Önce doğrula (enabled olsun ya da olmasın): doğrulanmamış adı LOG'a yazma
        // (log injection) ve geçersiz adı asla rclone'a iletme.
        validateFileName(fileName);
        if (!props.isEnabled()) {
            log.info("Drive sync köprüsü kapalı — deleteFromWaiting no-op ({}).", fileName);
            return;
        }

        List<String> args = buildDeleteArgs(fileName);
        CommandResult result = runRclone(args, "delete");
        if (!result.isSuccess()) {
            String stderr = result.stderr() == null ? "" : result.stderr().toLowerCase();
            // Olmayan dosya → idempotent: hata sayma.
            if (stderr.contains("not found") || stderr.contains("no such")
                    || stderr.contains("object not found") || stderr.contains("directory not found")) {
                log.info("Drive waiting'de silinecek dosya zaten yok (idempotent geçildi): {}", fileName);
                return;
            }
            log.error("rclone deletefile sıfırdan farklı çıkış kodu={} stderr={}",
                    result.exitCode(), result.stderr());
            throw new DriveSyncException(
                    "Drive waiting'den silme başarısız (rclone exit=" + result.exitCode() + ").");
        }
        log.info("Drive waiting'den silindi: {}", fileName);
    }

    // ---- argüman üretimi (test edilebilir; remote→local pull / remote delete) ----

    /**
     * rclone {@code copy} argümanları — yön: <b>remote → local</b> (PULL).
     * Kaynak HER ZAMAN {@code <remote>:<waitingRemotePath>}; hedef lokal dizin.
     * (Lokal→remote yönü bu metotta ÜRETİLEMEZ.)
     */
    List<String> buildPullArgs(Path localWaiting) {
        return List.of(
                props.getRcloneBinary(),
                "copy",
                remoteWaitingRoot(),
                localWaiting.toString());
    }

    /**
     * rclone {@code deletefile} argümanları — Drive {@code waiting/}'den TEK dosya silme.
     * Lokal yol KAYNAK olarak GEÇMEZ; yalnızca {@code <remote>:<waitingPath>/<fileName>}.
     */
    List<String> buildDeleteArgs(String fileName) {
        return List.of(
                props.getRcloneBinary(),
                "deletefile",
                remoteWaitingRoot() + "/" + fileName);
    }

    /** {@code <remote>:<waitingRemotePath>} biçimi (rclone remote yolu). */
    private String remoteWaitingRoot() {
        return props.getRemoteName() + ":" + props.getWaitingRemotePath();
    }

    // ---- yardımcılar ----

    private CommandResult runRclone(List<String> args, String op) {
        try {
            return commandRunner.run(args, Duration.ofSeconds(props.getTimeoutSeconds()));
        } catch (CommandExecutionException e) {
            // rclone yok / başlatılamadı / zaman aşımı → güvenli başarısızlık.
            log.error("rclone {} çalıştırılamadı (binary yok / zaman aşımı / kesinti): {}",
                    op, e.getMessage());
            throw new DriveSyncException("rclone çalıştırılamadı (kurulu değil veya erişilemez).", e);
        }
    }

    /** Lokal waiting dizinini çözer: yapılandırma boşsa storage kökü altındaki {@code waiting/}. */
    Path resolveLocalWaitingDir() {
        String configured = props.getLocalWaitingDir();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        String root = storageProperties.getRoot();
        if (root == null || root.isBlank()) {
            throw new DriveSyncException(
                    "Lokal waiting dizini çözülemedi: app.drive.local-waiting-dir ve app.storage.root boş.");
        }
        return Paths.get(root).resolve("waiting").toAbsolutePath().normalize();
    }

    /** Dizindeki düz dosya adlarını (alt-dizinler hariç) döndürür; dizin yoksa boş küme. */
    private Set<String> listFileNames(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            log.error("Lokal waiting dizini listelenemedi: {}", dir, e);
            throw new DriveSyncException("Lokal waiting dizini okunamadı.", e);
        }
        return names;
    }

    /**
     * Traversal/alt-dizin engeli: yalnızca düz dosya adına izin verilir. Bu bir ÇAĞIRAN
     * GİRDİSİ hatasıdır (dış bağımlılık değil) → {@link DriveSyncValidationException}
     * (handler 400'e çevirir; 502 DEĞİL).
     */
    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new DriveSyncValidationException("Silinecek dosya adı boş olamaz.");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")
                || fileName.contains(":")) {
            throw new DriveSyncValidationException(
                    "Geçersiz dosya adı (yol ayırıcı/traversal): " + fileName);
        }
    }
}
