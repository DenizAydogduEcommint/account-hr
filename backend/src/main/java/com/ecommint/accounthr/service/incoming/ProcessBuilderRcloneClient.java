package com.ecommint.accounthr.service.incoming;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecommint.accounthr.config.DriveSyncProperties;
import com.ecommint.accounthr.service.drive.CommandExecutionException;
import com.ecommint.accounthr.service.drive.CommandResult;
import com.ecommint.accounthr.service.drive.CommandRunner;

/**
 * {@link RcloneClient}'ın gerçek (production) implementasyonu (E5-02) — E2-06'nın subprocess
 * köprüsünü ({@link CommandRunner} + {@link ProcessBuilder}) YENİDEN KULLANIR. Komut yalnızca:
 * <pre>rclone copy &lt;remotePath&gt; &lt;localDir&gt;</pre>
 * yani <b>remote → local PULL</b>. Bu sınıfta push/upload/delete argümanı ÜRETİLEMEZ; remote
 * HER ZAMAN kaynaktır, lokal dizin HER ZAMAN hedeftir.
 *
 * <p>Güvenli başarısızlık: rclone yok / zaman aşımı / sıfırdan farklı çıkış kodu →
 * {@link RcloneException} (ham stderr yalnızca log'a, dışarı sızmaz).
 *
 * <p>Testlerde KULLANILMAZ: ITler {@code @MockBean RcloneClient} ile bu bean'i geçersiz kılar;
 * böylece CI'da gerçek rclone hiç exec edilmez.
 */
@Component
public class ProcessBuilderRcloneClient implements RcloneClient {

    private static final Logger log = LoggerFactory.getLogger(ProcessBuilderRcloneClient.class);

    private final DriveSyncProperties props;
    private final CommandRunner commandRunner;

    public ProcessBuilderRcloneClient(DriveSyncProperties props, CommandRunner commandRunner) {
        this.props = props;
        this.commandRunner = commandRunner;
    }

    @Override
    public void copyToLocal(String remotePath, Path localDir) {
        if (remotePath == null || remotePath.isBlank()) {
            throw new RcloneException("rclone copy için remote yol boş olamaz.");
        }
        if (localDir == null) {
            throw new RcloneException("rclone copy için hedef lokal dizin boş olamaz.");
        }
        try {
            Files.createDirectories(localDir);
        } catch (IOException e) {
            throw new RcloneException("Lokal landing dizini hazırlanamadı: " + localDir, e);
        }

        // Yön: remote → local (PULL). Kaynak HER ZAMAN remote; hedef HER ZAMAN lokal dizin.
        List<String> args = List.of(
                props.getRcloneBinary(),
                "copy",
                remotePath,
                localDir.toString());

        CommandResult result;
        try {
            result = commandRunner.run(args, Duration.ofSeconds(props.getTimeoutSeconds()));
        } catch (CommandExecutionException e) {
            log.error("rclone copy (incoming pull) çalıştırılamadı (binary yok / zaman aşımı): {}",
                    e.getMessage());
            throw new RcloneException("rclone çalıştırılamadı (kurulu değil veya erişilemez).", e);
        }

        if (!result.isSuccess()) {
            log.error("rclone copy (incoming pull) sıfırdan farklı çıkış kodu={} stderr={}",
                    result.exitCode(), result.stderr());
            throw new RcloneException(
                    "Drive waiting pull başarısız (rclone exit=" + result.exitCode() + ").");
        }
        log.info("rclone copy (incoming pull) tamam: {} → {}", remotePath, localDir);
    }
}
