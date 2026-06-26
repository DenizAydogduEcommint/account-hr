package com.ecommint.accounthr.service;

/**
 * İstenen kaynak (ör. id ile servis) bulunamadığında fırlatılır → 404 NOT_FOUND
 * ({@code GlobalExceptionHandler}'da eşlenir). Standart {@code ErrorResponse} şekli
 * korunur.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
