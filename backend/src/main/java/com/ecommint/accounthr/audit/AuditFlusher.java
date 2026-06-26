package com.ecommint.accounthr.audit;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.AuditLog;
import com.ecommint.accounthr.repository.AppUserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * {@link AuditContext}'te biriken {@link AuditEntry}'leri {@code audit_log} satırlarına
 * çevirip kalıcı yapan Spring bean (E1-05).
 *
 * <p>Transaction commit'inden hemen önce, {@code AuditInterceptor.beforeTransactionCompletion}
 * içinden çağrılır; böylece audit kayıtları asıl mutasyonla AYNI transaction'da yazılır
 * (atomik). CREATE durumunda entity id'si flush sonrası atandığından (pre-completion
 * auto-flush), id buffer'daki entity referansından reflection ile bu noktada okunur.
 *
 * <p><b>E1-DR-4 — StatelessSession ile yazma.</b> Eskiden audit satırları Spring-Data
 * {@code AuditLogRepository.save(...)} ile yazılıyordu; bu, ManagedSession üzerinde YENİ bir
 * ORM flush tetikler. Bu flush, Hibernate'in {@code beforeTransactionCompletion} pre-completion
 * callback'i içinde belgelenmiş bir tehlikedir (beklenmedik re-flush döngüleri, re-entrant
 * interceptor). Artık satırlar, AYNI transaction'ın JDBC bağlantısı üzerinde açılan bir
 * {@link StatelessSession} ile DOĞRUDAN INSERT edilir:
 * <ul>
 *   <li><b>Atomiklik:</b> {@code session.doWork(...)} bize asıl transaction'ın bağlantısını
 *       verir; o bağlantıda açılan StatelessSession aynı fiziksel transaction'a katılır →
 *       audit + veri birlikte commit/rollback olur.</li>
 *   <li><b>Nested flush YOK:</b> StatelessSession persistence context kullanmaz; {@code insert}
 *       doğrudan SQL üretir, ManagedSession'ı tekrar flush ETMEZ → re-flush tehlikesi yok.</li>
 *   <li><b>Re-entrancy YOK:</b> StatelessSession interceptor'ı yeniden tetiklemez (zaten
 *       {@code AuditLog} denetlenmez).</li>
 * </ul>
 *
 * <p><b>{@code changedAt}:</b> StatelessSession JPA auditing listener'larını ({@code @CreatedDate})
 * ÇALIŞTIRMAZ; bu yüzden zaman damgası burada AÇIKÇA set edilir (eski {@code repository.save}
 * yolunda {@code AuditingEntityListener} dolduruyordu) — davranış aynen korunur (non-null).
 *
 * <p>{@code changedBy}, {@link SecurityAuditorAware} ile geçerli kullanıcı id'sinden çözülür;
 * kimliksiz/sistem işlemlerinde NULL kalır.
 */
@Component
public class AuditFlusher {

    private static final Logger log = LoggerFactory.getLogger(AuditFlusher.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final AppUserRepository userRepository;
    private final SecurityAuditorAware auditorAware;

    public AuditFlusher(AppUserRepository userRepository,
            SecurityAuditorAware auditorAware) {
        this.userRepository = userRepository;
        this.auditorAware = auditorAware;
    }

    /**
     * Biriken tüm audit kayıtlarını {@code audit_log}'a yazar. Tampon boşsa hiçbir şey yapmaz.
     *
     * <p>Asıl mutasyonun transaction'ı HÂLÂ AKTİFKEN (pre-completion) çağrılır. ID'ler bu noktada
     * pre-completion auto-flush ile atanmış olduğundan {@link #readId} gerçek (null olmayan)
     * generated id'yi okur. Satırlar, aşağıda açıklanan StatelessSession ile aynı transaction'a
     * yazılır.
     */
    public void flush() {
        List<AuditEntry> entries = AuditContext.drain();
        if (entries.isEmpty()) {
            return;
        }

        AppUser changedBy = resolveChangedBy();
        LocalDateTime now = LocalDateTime.now();

        // Asıl mutasyonun ManagedSession'ı (ve dolayısıyla asıl transaction bağlantısı).
        Session session = entityManager.unwrap(Session.class);
        SessionFactory sessionFactory = session.getSessionFactory();

        // doWork: asıl transaction'ın JDBC bağlantısını verir. O bağlantıda açılan StatelessSession
        // aynı transaction'a katılır (atomik). Bağlantı asıl session'a ait olduğundan StatelessSession
        // KAPATILSA da bağlantıyı kapatmaz (Hibernate yalnızca kendi açtığı bağlantıyı kapatır).
        session.doWork(connection -> {
            try (StatelessSession stateless = sessionFactory.openStatelessSession(connection)) {
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
                    // StatelessSession @CreatedDate'i tetiklemez → changedAt'i elle doldur (non-null).
                    row.setChangedAt(now);
                    stateless.insert(row);
                }
            }
        });
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
