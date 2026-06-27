package com.ecommint.accounthr.service;

/**
 * Sistemdeki son aktif ADMIN'in rolünü düşürme veya pasifleştirme girişiminde
 * fırlatılır → 409 CONFLICT ({@code GlobalExceptionHandler}'da {@code LAST_ADMIN}
 * olarak eşlenir). Sistemde her zaman en az bir aktif yönetici kalmalıdır;
 * kullanıcı kendi üzerinde işlem yapsa bile bu değişmez.
 */
public class LastAdminException extends RuntimeException {

    public LastAdminException(String message) {
        super(message);
    }
}
