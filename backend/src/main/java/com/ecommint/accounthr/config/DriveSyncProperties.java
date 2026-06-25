package com.ecommint.accounthr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Drive {@code waiting/} senkron köprüsü yapılandırması (E2-06). {@code app.drive.*}.
 *
 * <p>Bu köprü rclone subprocess'i ile çalışır: Drive'daki {@code faturalar/waiting/}
 * dizinini lokale <b>çeker (pull)</b> ve işlenen dosyayı Drive {@code waiting/}'den
 * <b>siler (delete)</b>. Başka HİÇBİR Drive yazımı yapılmaz — özellikle lokal dosya
 * Drive'a ASLA push/upload edilmez (bkz. {@code CLAUDE.md} "Drive'a push edilmez").
 *
 * <p>Tüm değerler {@code application.yml}/env'den gelir; kod içine gömülü hiçbir
 * remote adı / klasör yolu / binary yolu YOKTUR. Drive kimlik bilgileri (OAuth token)
 * rclone'un kendi {@code rclone.conf} dosyasında tutulur; backend bu token'ları
 * doğrudan ELLEMEZ — sahip olduğumuz tek yapılandırma remote adı, yollar ve binary'dir.
 */
@ConfigurationProperties(prefix = "app.drive")
public class DriveSyncProperties {

    /** Köprü açık mı? Varsayılan {@code false} (güvenli kapalı; env ile açılır). */
    private boolean enabled = false;

    /** rclone remote adı (ör. {@code gdrive-ecommint}). rclone.conf içinde tanımlı olmalı. */
    private String remoteName = "gdrive-ecommint";

    /** Drive root folder ID (bilgi/dökümantasyon amaçlı; remote yapılandırmasıyla eşleşir). */
    private String rootFolderId;

    /** Remote üzerindeki waiting yolu (remote köküne göre). Varsayılan {@code faturalar/waiting}. */
    private String waitingRemotePath = "faturalar/waiting";

    /**
     * Pull edilen waiting dosyalarının yazılacağı lokal dizin (mutlak yol). Boş/eksikse
     * servis storage kökü altındaki {@code waiting/}'i kullanır (E1-04 ile aynı dizin).
     */
    private String localWaitingDir;

    /** rclone çalıştırılabilir dosyası (PATH'te ise sadece {@code rclone}). */
    private String rcloneBinary = "rclone";

    /** Her rclone alt-süreci için zaman aşımı (saniye). Varsayılan 120 sn. */
    private long timeoutSeconds = 120;

    /**
     * Zamanlanmış (scheduled) otomatik pull açık mı? Varsayılan {@code false}.
     * Bu görevde @Scheduled bir job YOK; bu bayrak ileride eklenecek bir job için
     * güvenli-kapalı kapıdır ({@code @ConditionalOnProperty} ile korunur).
     */
    private boolean scheduledEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    public String getRootFolderId() {
        return rootFolderId;
    }

    public void setRootFolderId(String rootFolderId) {
        this.rootFolderId = rootFolderId;
    }

    public String getWaitingRemotePath() {
        return waitingRemotePath;
    }

    public void setWaitingRemotePath(String waitingRemotePath) {
        this.waitingRemotePath = waitingRemotePath;
    }

    public String getLocalWaitingDir() {
        return localWaitingDir;
    }

    public void setLocalWaitingDir(String localWaitingDir) {
        this.localWaitingDir = localWaitingDir;
    }

    public String getRcloneBinary() {
        return rcloneBinary;
    }

    public void setRcloneBinary(String rcloneBinary) {
        this.rcloneBinary = rcloneBinary;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isScheduledEnabled() {
        return scheduledEnabled;
    }

    public void setScheduledEnabled(boolean scheduledEnabled) {
        this.scheduledEnabled = scheduledEnabled;
    }
}
