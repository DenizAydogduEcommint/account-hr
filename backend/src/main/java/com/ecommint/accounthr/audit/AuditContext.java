package com.ecommint.accounthr.audit;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Aktif transaction içinde yakalanan audit kayıtlarını biriktiren tampon (E1-05).
 *
 * <p><b>Neden ThreadLocal değil de transaction-bound?</b> Hibernate interceptor
 * callback'leri (onFlushDirty/onSave/onDelete) flush sırasında çalışır; bu noktada yeni
 * entity persist etmek güvenli değildir. Bunun yerine {@link AuditEntry} nesneleri burada
 * biriktirilir ve transaction commit'inden HEMEN ÖNCE {@code audit_log}'a yazılır.
 *
 * <p>Tampon Spring {@link TransactionSynchronizationManager} kaynağı olarak <b>aktif
 * transaction'a</b> bağlanır (raw thread'e DEĞİL). Böylece aynı thread'de iç içe /
 * {@code REQUIRES_NEW} transaction'lar KENDİ tamponlarını kullanır; biri diğerinin
 * kayıtlarını boşaltamaz/drain edemez. Flush/clear, {@code AuditInterceptor}'ın
 * Hibernate transaction callback'lerinden yönetilir; ROLLBACK'te kayıtlar yazılmaz
 * (bkz. {@code AuditInterceptor.beforeTransactionCompletion}).
 *
 * <p><b>Transaction dışı yol:</b> Aktif bir transaction synchronization yoksa kaydı kalıcı
 * yapmanın yolu yoktur ({@code AuditFlusher} transaction commit'inde çalışır). Bu durumda
 * kayıt sessizce ATILIR — ThreadLocal'da biriktirmek hiç temizlenmeyeceği için sızıntı
 * olurdu. Audit altyapısı asıl işlemi asla bozmamalıdır; transaction'sız mutasyon zaten
 * denetlenmez.
 */
public final class AuditContext {

    /** TSM resource anahtarı — tampon bu anahtarla aktif transaction'a bağlanır. */
    private static final Object RESOURCE_KEY = new Object();

    private AuditContext() {
    }

    /**
     * Bir audit kaydını aktif transaction'ın tamponuna ekler. Aktif synchronization yoksa
     * (transaction dışı yol) kayıt atılır — sızıntı/yanlış-flush olmaması için.
     */
    @SuppressWarnings("unchecked")
    public static void add(AuditEntry entry) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return; // transaction dışı: flush edilemez → biriktirme (leak olmasın)
        }
        List<AuditEntry> buffer =
                (List<AuditEntry>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (buffer == null) {
            buffer = new ArrayList<>();
            TransactionSynchronizationManager.bindResource(RESOURCE_KEY, buffer);
        }
        buffer.add(entry);
    }

    /**
     * Aktif transaction'ın tamponundaki kayıtları döndürür ve tamponu boşaltır (kopya
     * döner; resource bağlı kalır, unbind {@link #clear()} ile yapılır).
     */
    public static List<AuditEntry> drain() {
        List<AuditEntry> buffer = currentBuffer();
        if (buffer == null || buffer.isEmpty()) {
            return List.of();
        }
        List<AuditEntry> copy = new ArrayList<>(buffer);
        buffer.clear();
        return copy;
    }

    /** Aktif transaction'ın tamponu boş mu? (transaction yoksa boş sayılır.) */
    public static boolean isEmpty() {
        List<AuditEntry> buffer = currentBuffer();
        return buffer == null || buffer.isEmpty();
    }

    /**
     * Bu transaction'ın tamponunu çözer/temizler: aktif transaction'ın TSM resource'unu
     * unbind eder. Transaction dışı yolda biriken bir tampon olmadığından temizlenecek
     * bir şey yoktur.
     */
    public static void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.hasResource(RESOURCE_KEY)) {
            TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<AuditEntry> currentBuffer() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            return (List<AuditEntry>) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        }
        return null;
    }
}
