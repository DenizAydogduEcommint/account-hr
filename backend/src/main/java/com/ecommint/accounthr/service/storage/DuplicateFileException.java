package com.ecommint.accounthr.service.storage;

/**
 * Aynı (provider, invoice_no) ikilisi VEYA aynı SHA-256 içeriği zaten depolandığında
 * fırlatılır. Çağıran (controller) bunu 409 CONFLICT'e çevirir; nihai karar
 * (Invoice yerine Receipt'i atma vb.) iş katmanına aittir.
 */
public class DuplicateFileException extends StorageException {

    public DuplicateFileException(String message) {
        super(message);
    }
}
