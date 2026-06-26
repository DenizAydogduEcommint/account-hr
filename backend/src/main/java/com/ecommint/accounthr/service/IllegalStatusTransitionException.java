package com.ecommint.accounthr.service;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E3-07 — İzin verilmeyen fatura durumu geçişi (state machine ihlali). MVP'de politika
 * permissive olduğundan bu hata FIRLATILMAZ; ancak {@link InvoiceStatusPolicy} matrisi
 * ileride bir geçişi kapattığında PATCH ucu bu hatayı atar. {@code GlobalExceptionHandler}
 * bunu 409 {@code ILLEGAL_STATUS_TRANSITION} olarak eşler (çakışma: kaynağın mevcut
 * durumu hedefe geçişe izin vermiyor).
 */
public class IllegalStatusTransitionException extends RuntimeException {

    public IllegalStatusTransitionException(InvoiceStatus from, InvoiceStatus to) {
        super("Bu durum geçişine izin verilmiyor: " + from + " → " + to);
    }
}
