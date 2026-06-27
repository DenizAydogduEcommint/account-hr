package com.ecommint.accounthr.service;

/**
 * Aynı e-posta adresiyle kullanıcı oluşturulmaya çalışıldığında fırlatılır →
 * 409 CONFLICT ({@code GlobalExceptionHandler}'da {@code DUPLICATE_EMAIL} olarak
 * eşlenir). Standart {@code ErrorResponse} şekli korunur.
 */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
