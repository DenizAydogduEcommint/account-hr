package com.ecommint.accounthr.domain.enums;

/**
 * Fatura durumu. UI renkleri DB'de TUTULMAZ (frontend sabiti):
 * FOUND→4CAF50, E_INVOICE→8BC34A, EXPECTED→FF4444,
 * TO_INVESTIGATE→FF9800, IGNORED→FF9800.
 */
public enum InvoiceStatus {
    FOUND,
    E_INVOICE,
    EXPECTED,
    TO_INVESTIGATE,
    IGNORED
}
