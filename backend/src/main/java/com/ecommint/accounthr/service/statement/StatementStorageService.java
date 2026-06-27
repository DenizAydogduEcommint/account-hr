package com.ecommint.accounthr.service.statement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecommint.accounthr.config.StorageProperties;

/**
 * E4-01 — Yüklenen ekstre dosyasını denetim için STORAGE_ROOT altında
 * {@code statements/{YYYY-MM}/} klasörüne saklar. Fatura depolamasından (E1-04
 * {@code FileSystemStorageService}) BAĞIMSIZDIR: ekstre bir fatura değildir, FileAsset
 * üretmez, fatura-tarihi klasörleme/duplicate kuralları uygulanmaz.
 *
 * <p>Drive aynası {@code expenses/faturalar}'a ASLA dokunmaz — yalnızca STORAGE_ROOT içine
 * yazar. Saklama best-effort'tur: bir I/O hatası parse akışını bloklamaz.
 */
@Service
public class StatementStorageService {

    private static final Logger log = LoggerFactory.getLogger(StatementStorageService.class);

    static final String STATEMENTS_DIR = "statements";

    private final StorageProperties properties;

    public StatementStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    /**
     * Dosyayı {@code statements/{YYYY-MM}/{sha256}{ext}} olarak saklar. Best-effort: hata
     * fırlatmaz, yalnızca loglar (saklama parse'i bloklamamalı). Idempotent: aynı sha256
     * adlı dosya zaten varsa yeniden yazmaz.
     */
    public void storeQuietly(byte[] content, String filename, String sha256, YearMonth month) {
        try {
            Path root = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
            Path dir = root.resolve(STATEMENTS_DIR).resolve(month.toString());
            Files.createDirectories(dir);
            String ext = extensionOf(filename);
            Path target = dir.resolve(sha256 + ext);
            if (Files.exists(target)) {
                return; // idempotent
            }
            Path tmp = Files.createTempFile(dir, ".stmt-", ".tmp");
            try {
                Files.write(tmp, content);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.deleteIfExists(tmp);
                Files.write(target, content);
            }
            log.info("Ekstre saklandı: {}", root.relativize(target));
        } catch (IOException | RuntimeException e) {
            log.warn("Ekstre saklanamadı (best-effort, akış sürer): {}", e.getMessage());
        }
    }

    /** Dosya adından sanitize edilmiş uzantı (".xlsx" gibi); yoksa boş. */
    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String name = Paths.get(filename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return ext.isEmpty() ? "" : "." + ext;
    }
}
