package com.ecommint.accounthr.audit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Geçerli kullanıcının id'sini SecurityContext'ten çözen {@link AuditorAware} (E1-05).
 *
 * <p>JWT filtresi authentication'ın principal'ını kullanıcının e-postası olarak
 * yerleştirir (bkz. {@code JwtAuthenticationFilter}); burada e-postadan kullanıcı id'sine
 * çevrilir. Kimliksiz / sistem işlemlerinde {@link Optional#empty()} döner → audit
 * satırında {@code changed_by} NULL kalır.
 *
 * <p>Bu bean hem {@code audit_log.changed_by} doldurmak için audit altyapısında, hem de
 * ileride {@code @CreatedBy/@LastModifiedBy} gerekirse kullanılabilir.
 *
 * <p><b>DİKKAT — auto-flush rekürsiyonu:</b> Bu çağrı Hibernate'in {@code preUpdate}
 * audit callback'inden (flush sırasında) tetiklenir. E-posta→id sorgusu, default
 * {@code FlushModeType.AUTO} ile çalışırsa Hibernate sorgudan ÖNCE bekleyen kirli
 * entity'leri tekrar flush eder → tekrar {@code preUpdate} → tekrar bu sorgu →
 * {@link StackOverflowError}. Bu yüzden lookup, {@link FlushModeType#COMMIT} ile (flush
 * tetiklemeden) ve {@link EntityManager} üzerinden YAPILIR; Spring Data derived query
 * yerine doğrudan JPQL kullanılır. Sorgu salt-okunur olduğundan {@code COMMIT} güvenli.
 */
@Component
public class SecurityAuditorAware implements AuditorAware<Long> {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String email) || "anonymousUser".equals(email)) {
            return Optional.empty();
        }
        // FlushModeType.COMMIT: sorgu, bekleyen kirli entity'leri flush ETMEZ →
        // audit callback içinden çağrıldığında auto-flush rekürsiyonu (StackOverflow) olmaz.
        TypedQuery<Long> query = entityManager
                .createQuery("select u.id from AppUser u where u.email = :email", Long.class)
                .setParameter("email", email);
        query.setFlushMode(FlushModeType.COMMIT);
        List<Long> ids = query.getResultList();
        return ids.isEmpty() ? Optional.empty() : Optional.ofNullable(ids.get(0));
    }
}
