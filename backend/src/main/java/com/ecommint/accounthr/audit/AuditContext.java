package com.ecommint.accounthr.audit;

import java.util.ArrayList;
import java.util.List;

/**
 * Geçerli thread/transaction içinde yakalanan audit kayıtlarını biriktiren ThreadLocal
 * tampon (E1-05).
 *
 * <p>Hibernate interceptor callback'leri (onFlushDirty/onSave/onDelete) flush sırasında
 * çalışır; bu noktada yeni entity persist etmek güvenli değildir. Bunun yerine
 * {@link AuditEntry} nesneleri burada biriktirilir ve transaction commit'inden HEMEN
 * ÖNCE (bkz. {@code AuditFlusher}) tek seferde {@code audit_log}'a yazılır. Böylece
 * audit kayıtları işlemle aynı transaction'da, atomik biçimde kalıcı olur.
 */
public final class AuditContext {

    private static final ThreadLocal<List<AuditEntry>> ENTRIES =
            ThreadLocal.withInitial(ArrayList::new);

    private AuditContext() {
    }

    public static void add(AuditEntry entry) {
        ENTRIES.get().add(entry);
    }

    public static List<AuditEntry> drain() {
        List<AuditEntry> current = ENTRIES.get();
        List<AuditEntry> copy = new ArrayList<>(current);
        current.clear();
        return copy;
    }

    public static boolean isEmpty() {
        return ENTRIES.get().isEmpty();
    }

    public static void clear() {
        ENTRIES.remove();
    }
}
