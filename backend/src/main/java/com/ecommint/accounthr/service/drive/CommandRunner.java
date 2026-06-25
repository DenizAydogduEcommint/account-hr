package com.ecommint.accounthr.service.drive;

import java.time.Duration;
import java.util.List;

/**
 * Alt-süreç (subprocess) çalıştırma soyutlaması (E2-06).
 *
 * <p>{@link com.ecommint.accounthr.service.drive.DriveSyncService} doğrudan
 * {@link ProcessBuilder} kullanmaz; bunun yerine bu arayüz üzerinden çalışır.
 * Böylece birim testlerde GERÇEK bir {@code rclone} çağırmadan, sahte (fake) bir
 * runner ile davranış doğrulanabilir.
 *
 * <p>Tek metodu vardır: argüman listesini verilen zaman aşımıyla çalıştırır ve
 * {@link CommandResult} döndürür. Binary bulunamaması, başlatılamama veya zaman
 * aşımı durumunda {@link CommandExecutionException} fırlatır (servis bunu güvenli
 * başarısızlığa çevirir).
 */
public interface CommandRunner {

    /**
     * @param args    çalıştırılacak komut + argümanlar (ilk eleman binary'dir)
     * @param timeout süreç tamamlanması için maksimum süre
     * @return {@link CommandResult} (exit code + stdout + stderr)
     * @throws CommandExecutionException binary yok / başlatılamadı / zaman aşımı / kesinti
     */
    CommandResult run(List<String> args, Duration timeout);
}
