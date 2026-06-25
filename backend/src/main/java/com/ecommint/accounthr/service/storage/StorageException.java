package com.ecommint.accounthr.service.storage;

/**
 * Depolama katmanı genel hatası (I/O, geçersiz girdi vb.). Path-traversal ve
 * benzeri güvenlik ihlalleri bunun alt türü {@link StoragePathTraversalException}
 * ile temsil edilir.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
