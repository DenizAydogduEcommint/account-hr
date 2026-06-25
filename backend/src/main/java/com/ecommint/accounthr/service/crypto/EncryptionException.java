package com.ecommint.accounthr.service.crypto;

/**
 * Credential şifreleme/çözme sırasında oluşan hata (E1-05). Mesaja ASLA düz metin
 * veya anahtar konmaz.
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
