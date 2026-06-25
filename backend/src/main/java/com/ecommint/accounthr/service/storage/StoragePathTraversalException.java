package com.ecommint.accounthr.service.storage;

/**
 * Çözümlenen dosya yolu storage kökünün DIŞINA çıktığında fırlatılır
 * (path traversal / `..` denemeleri). Controller bunu 400 BAD_REQUEST'e çevirir.
 */
public class StoragePathTraversalException extends StorageException {

    public StoragePathTraversalException(String message) {
        super(message);
    }
}
