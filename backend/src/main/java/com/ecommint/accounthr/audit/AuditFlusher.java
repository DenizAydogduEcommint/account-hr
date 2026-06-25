package com.ecommint.accounthr.audit;

import java.lang.reflect.Method;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.AuditLog;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.AuditLogRepository;

/**
 * {@link AuditContext}'te biriken {@link AuditEntry}'leri {@code audit_log} satırlarına
 * çevirip kalıcı yapan Spring bean (E1-05).
 *
 * <p>Transaction commit'inden hemen önce ({@code TransactionSynchronization.beforeCommit})
 * çağrılır; böylece audit kayıtları asıl mutasyonla AYNI transaction'da yazılır (atomik).
 * CREATE durumunda entity id'si flush sonrası atandığından, id buffer'daki entity
 * referansından reflection ile bu noktada okunur.
 *
 * <p>{@code changedBy}, {@link SecurityAuditorAware} ile geçerli kullanıcı id'sinden
 * çözülür; kimliksiz/sistem işlemlerinde NULL kalır.
 */
@Component
public class AuditFlusher {

    private static final Logger log = LoggerFactory.getLogger(AuditFlusher.class);

    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository userRepository;
    private final SecurityAuditorAware auditorAware;

    public AuditFlusher(AuditLogRepository auditLogRepository,
            AppUserRepository userRepository,
            SecurityAuditorAware auditorAware) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.auditorAware = auditorAware;
    }

    /** Biriken tüm audit kayıtlarını flush eder. Tampon boşsa hiçbir şey yapmaz. */
    public void flush() {
        List<AuditEntry> entries = AuditContext.drain();
        if (entries.isEmpty()) {
            return;
        }

        AppUser changedBy = resolveChangedBy();

        for (AuditEntry entry : entries) {
            Long entityId = readId(entry.getEntityRef());
            if (entityId == null) {
                // id çözülemezse (beklenmedik) kaydı atla — audit asla işlemi bozmamalı.
                log.warn("Audit kaydı atlandı: entityType={} action={} (id çözülemedi)",
                        entry.getEntityType(), entry.getAction());
                continue;
            }
            AuditLog row = new AuditLog();
            row.setEntityType(entry.getEntityType());
            row.setEntityId(entityId);
            row.setAction(entry.getAction());
            row.setFieldName(entry.getFieldName());
            row.setOldValue(entry.getOldValue());
            row.setNewValue(entry.getNewValue());
            row.setNote(entry.getNote());
            row.setChangedBy(changedBy);
            auditLogRepository.save(row);
        }
    }

    private AppUser resolveChangedBy() {
        return auditorAware.getCurrentAuditor()
                .flatMap(userRepository::findById)
                .orElse(null);
    }

    private Long readId(Object entity) {
        try {
            Method getId = entity.getClass().getMethod("getId");
            Object id = getId.invoke(entity);
            return id instanceof Long longId ? longId : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
