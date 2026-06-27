package com.ecommint.accounthr.audit;

import java.util.Set;

import org.hibernate.Interceptor;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecommint.accounthr.domain.AppUser;
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
 * <p>Yakalanan kayıtlar {@link AuditContext}'e (aktif transaction'a bağlı tampon) eklenir;
 * gerçek INSERT, transaction commit'inden hemen önce {@code AuditFlusher} tarafından yapılır.
 *
 * <p><b>E1-DR-4 — yazma artık nested-flush YAPMAYAN bir StatelessSession ile.</b> Eskiden
 * {@code beforeTransactionCompletion} içinden Spring-Data {@code AuditLogRepository.save(...)}
 * çağrılıyordu; bu, ManagedSession üzerinde YENİ BİR ORM flush tetikler — Hibernate'in
 * pre-completion callback'i içinde belgelenmiş bir tehlikedir (beklenmedik re-flush döngüleri,
 * re-entrant interceptor). Bu refactor'da yakalama ({@code onSave/onFlushDirty/onDelete}) AYNEN
 * kalır; yalnızca YAZMA mekanizması değişir: {@link AuditFlusher}, audit satırlarını AYNI
 * transaction'ın JDBC bağlantısı üzerinde açılan bir {@code StatelessSession} ile doğrudan
 * INSERT eder — Spring-Data yok, nested ORM flush yok, re-entrancy yok. Atomiklik korunur
 * (aynı bağlantı/transaction); ROLLBACK'te kayıtlar yazılmaz (aşağıdaki rollback koruması).
 *
 * <p>HASSAS alanlar (parola, parola hash'i, token, şifreli secret, master key) audit'e
 * ASLA yazılmaz — {@link #SENSITIVE_FIELDS} ile atlanır. Bu interceptor zaten
 * {@code AppUser}/{@code RefreshToken}/{@code ServiceCredential} gibi hassas entity'leri
 * denetlemez; ek güvenlik olarak alan adı bazlı filtre de uygulanır.
 */
public class AuditInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    /**
     * Denetlenen entity'lerin basit (simple) adları → audit_log.entity_type.
     *
     * <p>{@code AppUser} erişim-kontrol izlenebilirliği için denetlenir (kullanıcıyı kim
     * oluşturdu, rolü/aktifliğini/e-postasını kim değiştirdi). Hassas alanlar (özellikle
     * {@code passwordHash}) {@link #SENSITIVE_FIELDS} ile FİLTRELENİR ve audit'e ASLA
     * yazılmaz; ayrıca CREATE yolu ({@code onSave}) yalnızca entity tipini + id'sini yazar
     * (hiçbir alan değeri yakalamaz), bu yüzden yeni kullanıcının hash'i CREATE'te de sızmaz.</p>
     */
    private static final Set<String> AUDITED_TYPES =
            Set.of("Invoice", "FileAsset", "Service", "AppUser");

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
        if (entity instanceof AppUser) {
            return "AppUser";
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
            // CREATE yolu HİÇBİR alan değeri yakalamaz — yalnızca entity tipi + id (flush
            // sonrası reflection ile) yazılır. fieldName/oldValue/newValue daima NULL'dır.
            // Bu nedenle yeni bir AppUser'ın passwordHash'i (veya herhangi bir hassas alanı)
            // CREATE audit satırına ASLA ulaşamaz; SENSITIVE_FIELDS filtresi UPDATE
            // (onFlushDirty) yolunda alan-bazlı uygulanır.
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

    /**
     * Audit kaydını AKTİF TRANSACTION'a bağlı tampona ekler (bkz. {@link AuditContext}).
     * Tampon transaction-bound olduğu için aynı thread'deki {@code REQUIRES_NEW} iç
     * transaction dıştakinin tamponunu paylaşmaz/boşaltamaz.
     */
    private void enqueue(AuditEntry entry) {
        AuditContext.add(entry);
    }

    /**
     * Transaction completion'dan HEMEN ÖNCE Hibernate tarafından çağrılır (bu transaction'a
     * özgü). Bu noktada Hibernate'in pre-completion auto-flush'ı çalıştığı için dirty-check
     * değişiklikleri ({@code onFlushDirty}) yakalanmış ve CREATE id'leri atanmıştır.
     *
     * <p>E1-DR-4: Bu callback yalnızca YAZMAYI TETİKLER. Asıl INSERT, {@link AuditFlusher}
     * tarafından AYNI transaction bağlantısı üzerinde bir {@code StatelessSession} ile yapılır
     * (nested ORM flush YOK; eski {@code repository.save} tehlikesi giderildi).
     *
     * <p>ROLLBACK koruması: callback hem commit hem rollback yolunda tetiklenir. Transaction
     * rollback'e işaretlenmişse audit kayıtları YAZILMAZ — yalnızca tampon temizlenir; aksi
     * halde rollback edilen bir işlemin audit satırları yanlışlıkla commit edilebilirdi.
     */
    @Override
    public void beforeTransactionCompletion(Transaction tx) {
        if (AuditContext.isEmpty()) {
            return;
        }
        if (isRollingBack(tx)) {
            // Rollback yolunda audit satırı yazma; bu transaction'ın tamponunu boşalt.
            AuditContext.drain();
            return;
        }
        AuditFlusher flusher = AuditFlusherHolder.get();
        if (flusher != null) {
            // Audit YAZIMI asıl işlemi ASLA bozmamalı: beforeTransactionCompletion içinden
            // exception fırlatmak Hibernate'te TANIMSIZ davranıştır (commit eden iş
            // transaction'ını abort/corrupt edebilir). Bu yüzden flush hatasını ERROR olarak
            // (traceId/correlationId MDC'de) logla ve YUT — yeniden fırlatma. Audit kaybı
            // kabul edilebilir; iş verisinin bütünlüğü kabul edilemez.
            try {
                flusher.flush();
            } catch (RuntimeException e) {
                log.error("Audit flush başarısız — iş transaction'ı korunuyor (yutuldu): {}",
                        e.getMessage(), e);
            }
        } else {
            // Flusher hazır değilse tamponu boşalt (sızıntı olmasın); yalnızca context
            // boot olmadan teorik olarak mümkündür.
            AuditContext.drain();
        }
    }

    @Override
    public void afterTransactionCompletion(Transaction tx) {
        // Bu transaction'ın TSM resource'unu çöz.
        AuditContext.clear();
    }

    /** Transaction commit'e değil rollback'e gidiyor mu? Durum okunamazsa güvenli=false. */
    private boolean isRollingBack(Transaction tx) {
        if (tx == null) {
            return false;
        }
        try {
            TransactionStatus status = tx.getStatus();
            return status == TransactionStatus.MARKED_ROLLBACK
                    || status == TransactionStatus.ROLLING_BACK
                    || status == TransactionStatus.ROLLED_BACK
                    || status == TransactionStatus.FAILED_COMMIT;
        } catch (RuntimeException e) {
            return false;
        }
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
