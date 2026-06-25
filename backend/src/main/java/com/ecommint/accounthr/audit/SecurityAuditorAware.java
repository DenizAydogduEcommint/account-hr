package com.ecommint.accounthr.audit;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ecommint.accounthr.repository.AppUserRepository;

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
 */
@Component
public class SecurityAuditorAware implements AuditorAware<Long> {

    private final AppUserRepository userRepository;

    public SecurityAuditorAware(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

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
        return userRepository.findByEmail(email).map(u -> u.getId());
    }
}
