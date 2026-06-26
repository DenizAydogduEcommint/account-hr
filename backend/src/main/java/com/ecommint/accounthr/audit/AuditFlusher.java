package com.ecommint.accounthr.audit;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecommint.accounthr.domain.enums.AuditAction;

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
 * <p><b>E1-DR-4 (gözden geçirme) — RAW JDBC ile yazma.</b> Eskiden audit satırları Spring-Data
 * {@code AuditLogRepository.save(...)} ile yazılıyordu; bu, ManagedSession üzerinde YENİ bir
 * ORM flush tetikler — Hibernate'in {@code beforeTransactionCompletion} pre-completion callback'i
 * içinde belgelenmiş bir tehlikedir (re-flush döngüleri, re-entrant interceptor). Ara çözüm
 * {@code StatelessSession} idi; ancak StatelessSession'ı try-with-resources ile KAPATMAK
 * (sürüm-bağımlı) PAYLAŞILAN JDBC bağlantısını dış transaction commit etmeden serbest
 * bırakabiliyordu, ayrıca {@code insert}'e managed bir {@code AppUser} geçmek proxy-kırılgandı.
 *
 * <p>Artık satırlar, AYNI transaction'ın JDBC bağlantısı üzerinde DOĞRUDAN bir
 * {@link PreparedStatement} INSERT ile yazılır ({@code session.doWork(...)} içinden):
 * <ul>
 *   <li><b>Atomiklik:</b> {@code doWork} bize asıl transaction'ın bağlantısını verir; aynı
 *       fiziksel transaction'a yazılır → audit + veri birlikte commit/rollback olur.</li>
 *   <li><b>Bağlantı sahipliği transferi YOK:</b> StatelessSession kapatma riskini taşımayız;
 *       yalnızca {@code PreparedStatement} kapatılır, bağlantı asla kapatılmaz.</li>
 *   <li><b>ORM proxy traversal YOK:</b> {@code changed_by} ham bir {@code Long} FK değeri
 *       olarak yazılır (AppUser entity/proxy geçilmez).</li>
 *   <li><b>Persistence-context rekürsiyonu YOK:</b> ham SQL ManagedSession'ı tekrar flush
 *       etmez; nested-flush / re-entrancy tehlikesi yoktur.</li>
 * </ul>
 *
 * <p>{@code audit_log} tablosunun {@code version} kolonu YOKTUR (V13 optimistic-locking
 * version'ı audit_log'a EKLEMEDİ); bu yüzden INSERT'te version kolonu yer almaz.
 *
 * <p>{@code changed_by}: geçerli kullanıcının id'si {@link SecurityAuditorAware}'den ham
 * {@code Long} olarak çözülür ve FK değeri olarak yazılır; kimliksiz/sistem işlemlerinde NULL.
 *
 * <p>{@code changed_at}: her satır için AÇIKÇA {@code LocalDateTime.now()} (Timestamp) yazılır
 * (eski {@code AuditingEntityListener} yolundaki @CreatedDate doldurma davranışı korunur).
 */
@Component
public class AuditFlusher {

    private static final Logger log = LoggerFactory.getLogger(AuditFlusher.class);

    /**
     * audit_log INSERT'i — kolonlar AÇIKÇA listelenir. version kolonu YOK (audit_log'a
     * V13 version eklemedi). changed_at her satırda elle doldurulur (non-null).
     */
    private static final String INSERT_SQL =
            "INSERT INTO audit_log "
                    + "(entity_type, entity_id, action, field_name, old_value, new_value, "
                    + "note, changed_by, changed_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    @PersistenceContext
    private EntityManager entityManager;

    private final SecurityAuditorAware auditorAware;

    public AuditFlusher(SecurityAuditorAware auditorAware) {
        this.auditorAware = auditorAware;
    }

    /**
     * Biriken tüm audit kayıtlarını {@code audit_log}'a yazar. Tampon boşsa hiçbir şey yapmaz.
     *
     * <p>Asıl mutasyonun transaction'ı HÂLÂ AKTİFKEN (pre-completion) çağrılır. ID'ler bu noktada
     * pre-completion auto-flush ile atanmış olduğundan {@link #readId} gerçek (null olmayan)
     * generated id'yi okur. Satırlar, asıl transaction'ın JDBC bağlantısı üzerinde ham bir
     * {@link PreparedStatement} ile yazılır (atomik; ROLLBACK'te birlikte geri alınır).
     */
    public void flush() {
        List<AuditEntry> entries = AuditContext.drain();
        if (entries.isEmpty()) {
            return;
        }

        // changed_by: ham Long FK (AppUser proxy/entity DEĞİL). Yoksa (sistem) NULL.
        Long changedById = auditorAware.getCurrentAuditor().orElse(null);
        Timestamp changedAt = Timestamp.valueOf(LocalDateTime.now());

        // Asıl mutasyonun ManagedSession'ı (ve dolayısıyla asıl transaction bağlantısı).
        Session session = entityManager.unwrap(Session.class);

        // doWork: asıl transaction'ın JDBC bağlantısını verir → ham INSERT aynı transaction'a
        // yazılır. SADECE PreparedStatement kapatılır; bağlantı ASLA kapatılmaz (sahipliği
        // Hibernate/asıl transaction'da kalır).
        session.doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
                int batched = 0;
                for (AuditEntry entry : entries) {
                    Long entityId = readId(entry.getEntityRef());
                    if (entityId == null) {
                        // id çözülemezse (beklenmedik) kaydı atla — audit asla işlemi bozmamalı.
                        log.warn("Audit kaydı atlandı: entityType={} action={} (id çözülemedi)",
                                entry.getEntityType(), entry.getAction());
                        continue;
                    }
                    bindRow(ps, entry, entityId, changedById, changedAt);
                    ps.addBatch();
                    batched++;
                }
                if (batched > 0) {
                    ps.executeBatch();
                }
            }
        });
    }

    /** Tek bir audit satırını PreparedStatement'a bağlar (action STRING olarak yazılır). */
    private void bindRow(PreparedStatement ps, AuditEntry entry, Long entityId,
            Long changedById, Timestamp changedAt) throws SQLException {
        ps.setString(1, entry.getEntityType());
        ps.setLong(2, entityId);
        AuditAction action = entry.getAction();
        ps.setString(3, action != null ? action.name() : null);
        setNullableString(ps, 4, entry.getFieldName());
        setNullableString(ps, 5, entry.getOldValue());
        setNullableString(ps, 6, entry.getNewValue());
        setNullableString(ps, 7, entry.getNote());
        if (changedById != null) {
            ps.setLong(8, changedById);
        } else {
            ps.setNull(8, Types.BIGINT);
        }
        ps.setTimestamp(9, changedAt);
    }

    private static void setNullableString(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value != null) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
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
