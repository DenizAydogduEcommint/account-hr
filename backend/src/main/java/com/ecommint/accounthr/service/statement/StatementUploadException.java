package com.ecommint.accounthr.service.statement;

/**
 * Ekstre yükleme/onaylama (E4-01) isteğindeki çağıran-girdisi hatası — bilinmeyen kart,
 * biçimsiz ay, bilinmeyen batch vb. {@code GlobalExceptionHandler}'da 400
 * {@code VALIDATION_ERROR} olarak eşlenir; standart {@code ErrorResponse} şekli korunur.
 */
public class StatementUploadException extends RuntimeException {

    public StatementUploadException(String message) {
        super(message);
    }
}
