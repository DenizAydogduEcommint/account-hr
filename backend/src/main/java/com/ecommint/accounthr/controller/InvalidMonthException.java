package com.ecommint.accounthr.controller;

/**
 * E3-01 — Dashboard {@code month} parametresi format hatası.
 *
 * <p>{@code GET /api/v1/dashboard/summary?month=...} için verilen değer
 * {@code YYYY-MM} formatına uymuyorsa fırlatılır ve
 * {@link GlobalExceptionHandler} tarafından 400 {@code INVALID_MONTH}'a eşlenir.
 * Bilinmeyen ama iyi-biçimli ay (ör. 2026-09) hata DEĞİLDİR (sıfır özet döner);
 * yalnızca bozuk/biçimsiz string'ler bu hatayı tetikler.
 */
public class InvalidMonthException extends RuntimeException {

    public InvalidMonthException(String message) {
        super(message);
    }

    public InvalidMonthException(String message, Throwable cause) {
        super(message, cause);
    }
}
