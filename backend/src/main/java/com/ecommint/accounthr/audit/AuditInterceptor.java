package com.ecommint.accounthr.audit;

import java.util.Set;

import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;

import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Service;
import com.ecommint.accounthr.domain.enums.AuditAction;

/**
 * Kritik mutasyonları yakalayıp {@code audit_log}'a dönüştüren Hibernate interceptor
 * (E1-05).
 *
 * <p>Denetlenen entity'ler: {@link Invoice}, {@link FileAsset}, {@link Service}.
 * Yakalanan olaylar:
 * <ul>
 *   <li>{@code onSave} → CREATE</li>
 *   <li>{@code onFlushDirty} → alan bazlı UPDATE; Invoice.status değişimi STATUS_CHANGE
 *       (field_name=status, eski→yeni)</li>
 *   <li>{@code onDelete} → DELETE</li>
 * </ul>
 *
 * <p>Yakalanan kayıtlar {@link AuditContext}'e (ThreadLocal) eklenir; gerçek INSERT,
 * transaction commit'inden hemen önce {@code AuditFlusher} tarafından yapılır.
 *
 * <p>HASSAS alanlar (parola, parola hash'i, token, şifreli secret, master key) audit'e
 * ASLA yazılmaz — {@link #SENSITIVE_FIELDS} ile atlanır. Bu interceptor zaten
 * {@code AppUser}/{@code RefreshToken}/{@code ServiceCredential} gibi hassas entity'leri
 * denetlemez; ek güvenlik olarak alan adı bazlı filtre de uygulanır.
 */
public class AuditInterceptor implements Interceptor {

    /** Denetlenen entity'lerin basit (simple) adları → audit_log.entity_type. */
    private static final Set<String> AUDITED_TYPES =
            Set.of("Invoice", "FileAsset", "Service");

    /** Audit'e asla yazılmayacak alan adları (entity hangisi olursa olsun). */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwordHash", "secret", "token", "tokenHash",
            "masterKey", "refreshToken", "accessToken");

    /** Audit gürültüsü yaratan teknik alanlar (auditing zaman damgaları) — atlanır. */
    private static final Set<String> IGNORED_FIELDS = Set.of(
            "createdAt", "updatedAt", "changedAt");

    private static String typeOf(Object entity) {
        if (entity instanceof Invoice) {
            return "Invoice";
        }
        if (entity instanceof FileAsset) {
            return "FileAsset";
        }
        if (entity instanceof Service) {
            return "Service";
        }
        return null;
    }

    private boolean isAudited(Object entity) {
        String type = typeOf(entity);
        return type != null && AUDITED_TYPES.contains(type);
    }

    @Override
    public boolean onSave(Object entity, Object id, Object[] state,
            String[] propertyNames, Type[] types) {
        if (isAudited(entity)) {
            enqueue(new AuditEntry(
                    entity, typeOf(entity), AuditAction.CREATE,
                    null, null, null, null));
        }
        return false; // state'i değiştirmiyoruz
    }

    @Override
    public boolean onFlushDirty(Object entity, Object id, Object[] currentState,
            Object[] previousState, String[] propertyNames, Type[] types) {
        if (!isAudited(entity) || previousState == null) {
            return false;
        }
        String entityType = typeOf(entity);
        for (int i = 0; i < propertyNames.length; i++) {
            String field = propertyNames[i];
            if (SENSITIVE_FIELDS.contains(field) || IGNORED_FIELDS.contains(field)) {
                continue; // hassas veya teknik alan → audit'e yazma
            }
            Object oldVal = previousState[i];
            Object newVal = currentState[i];
            if (java.util.Objects.equals(oldVal, newVal)) {
                continue; // değişmemiş
            }
            AuditAction action = ("Invoice".equals(entityType) && "status".equals(field))
                    ? AuditAction.STATUS_CHANGE
                    : AuditAction.UPDATE;
            enqueue(new AuditEntry(
                    entity, entityType, action, field,
                    stringify(oldVal), stringify(newVal), null));
        }
        return false;
    }

    @Override
    public void onDelete(Object entity, Object id, Object[] state,
            String[] propertyNames, Type[] types) {
        if (isAudited(entity)) {
            enqueue(new AuditEntry(
                    entity, typeOf(entity), AuditAction.DELETE,
                    null, null, null, null));
        }
    }

    /** Audit kaydını ThreadLocal tampona ekler (commit'ten önce flush edilecek). */
    private void enqueue(AuditEntry entry) {
        AuditContext.add(entry);
    }

    /**
     * Transaction commit'inden HEMEN ÖNCE Hibernate tarafından çağrılır (DB commit'ten
     * önce, aynı transaction içinde). Burada biriken audit kayıtları {@code audit_log}'a
     * yazılır; INSERT'ler asıl mutasyonla aynı commit'e dâhil olur (atomik). Bu callback,
     * değişiklik ister erken (save/flush) ister commit anında dirty-check ile yakalansın
     * her durumda çalıştığı için synchronization-sırası tuzağından etkilenmez.
     */
    @Override
    public void beforeTransactionCompletion(Transaction tx) {
        if (AuditContext.isEmpty()) {
            return;
        }
        AuditFlusher flusher = AuditFlusherHolder.get();
        if (flusher != null) {
            flusher.flush();
        } else {
            // Flusher hazır değilse tamponu boşalt (sızıntı olmasın); audit kaybı loglanmaz
            // çünkü bu yalnızca context boot olmadan teorik olarak mümkün.
            AuditContext.drain();
        }
    }

    @Override
    public void afterTransactionCompletion(Transaction tx) {
        // Thread havuzu yeniden kullanımında sızıntı olmasın diye tamponu temizle.
        AuditContext.clear();
    }

    private static String stringify(Object value) {
        if (value == null) {
            return null;
        }
        // İlişkili entity'lerde id'yi tercih et (ör. provider → "Provider#3"); aksi
        // halde toString. Hassas içerik buraya gelmez (sensitive alanlar atlanır).
        try {
            java.lang.reflect.Method getId = value.getClass().getMethod("getId");
            Object idVal = getId.invoke(value);
            if (idVal != null) {
                return value.getClass().getSimpleName() + "#" + idVal;
            }
        } catch (ReflectiveOperationException ignored) {
            // getId yoksa düz toString'e düş
        }
        return String.valueOf(value);
    }
}
