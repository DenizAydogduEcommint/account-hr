package com.ecommint.accounthr.service;

/**
 * Elle harcama satırı (E3-06) isteğindeki SEMANTİK doğrulama hatası — alan-bazı Bean
 * Validation'ın yakalayamadığı, veri-bağımlı geçersizlikler için (ör. bilinmeyen
 * {@code cardLast4}, bilinmeyen {@code usingTeamId}). {@code GlobalExceptionHandler}'da
 * 400 {@code VALIDATION_ERROR} olarak eşlenir; standart {@code ErrorResponse} şekli korunur.
 */
public class InvalidExpenseRequestException extends RuntimeException {

    public InvalidExpenseRequestException(String message) {
        super(message);
    }
}
