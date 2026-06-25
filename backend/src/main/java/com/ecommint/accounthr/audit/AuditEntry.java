package com.ecommint.accounthr.audit;

import com.ecommint.accounthr.domain.enums.AuditAction;

/**
 * Tek bir audit kaydını temsil eden taşıyıcı (E1-05). Hibernate interceptor değişikliği
 * yakaladığında üretilir, transaction commit'inden hemen önce {@code audit_log}'a yazılır.
 *
 * <p>{@code entityRef} doğrudan entity nesnesini tutar; CREATE (INSERT) durumunda id
 * henüz atanmamış olabileceği için gerçek id flush sonrasında bu referanstan okunur.
 * Bu sınıfa HASSAS değer (parola, token, şifreli secret) ASLA konmaz — interceptor
 * bu alanları zaten atlar.
 */
public class AuditEntry {

    private final Object entityRef;
    private final String entityType;
    private final AuditAction action;
    private final String fieldName;
    private final String oldValue;
    private final String newValue;
    private final String note;

    public AuditEntry(Object entityRef, String entityType, AuditAction action,
            String fieldName, String oldValue, String newValue, String note) {
        this.entityRef = entityRef;
        this.entityType = entityType;
        this.action = action;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.note = note;
    }

    public Object getEntityRef() {
        return entityRef;
    }

    public String getEntityType() {
        return entityType;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getNote() {
        return note;
    }
}
