package com.ecommint.accounthr.service.incoming;

import java.nio.file.Path;

/**
 * rclone PULL (copy: remote → local) soyutlaması (E5-02).
 *
 * <p>Bu arayüz YALNIZCA Drive'dan lokale KOPYALAMA (pull) yeteneği sunar. Bilinçli olarak
 * push/upload (local → remote) ve delete metodu YOKTUR ve EKLENMEMELİDİR: "Drive'a lokal dosya
 * push edilmez" kuralı (CLAUDE.md) bu seam'e KODA gömülüdür. E5-02 bu round'da Drive'a hiçbir
 * yazma (push/delete) yapmaz — yalnızca {@link #copyToLocal(String, Path)} ile çeker.
 *
 * <p>Test edilebilirlik: {@link DriveWaitingPullService} doğrudan {@link ProcessBuilder}/rclone
 * çağırmaz; bu arayüz üzerinden çalışır. CI'da gerçek rclone YOKTUR; testler sahte (fake)/mock
 * bir {@code RcloneClient} enjekte ederek landing dizinine dosya bırakır — gerçek rclone hiç
 * exec edilmez.
 */
public interface RcloneClient {

    /**
     * Bir remote yolun TÜM içeriğini lokal bir dizine KOPYALAR (rclone {@code copy}; yön:
     * <b>remote → local</b>). Lokal kaynak ASLA remote'a yazılmaz.
     *
     * @param remotePath rclone remote yolu (ör. {@code gdrive-ecommint:faturalar/waiting})
     * @param localDir   hedef lokal dizin (mutlak); yoksa çağıran/impl oluşturur
     * @throws RcloneException rclone yok / sıfırdan farklı çıkış / zaman aşımı
     */
    void copyToLocal(String remotePath, Path localDir);
}
