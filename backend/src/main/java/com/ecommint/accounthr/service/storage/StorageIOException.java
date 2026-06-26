package com.ecommint.accounthr.service.storage;

/**
 * Sunucu tarafı I/O arızası (disk dolu, taşıma/kopyalama/temp-create/dizin-oluşturma
 * başarısızlığı) için {@link StorageException} alt türü. Bunlar çağıran girdisi hatası
 * DEĞİLDİR; {@code GlobalExceptionHandler} bunu 503 SERVICE_UNAVAILABLE'a eşler.
 *
 * <p>Girdi-doğrulama hataları (boş serviceName, null tarih vb.) düz
 * {@link StorageException} olarak kalır → 400 BAD_REQUEST.
 */
public class StorageIOException extends StorageException {

    public StorageIOException(String message) {
        super(message);
    }

    public StorageIOException(String message, Throwable cause) {
        super(message, cause);
    }
}
