package com.ecommint.accounthr.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.repository.AppUserRepository;

/**
 * AppUser'ı e-posta ile yükleyip Spring Security UserDetails'e çevirir.
 * Authority = ROLE_<UserRole> (ör. ROLE_ADMIN). Parola = BCrypt hash.
 * Pasif (active=false) kullanıcılar disabled olarak işaretlenir.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public CustomUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + email));
        return toUserDetails(user);
    }

    public UserDetails toUserDetails(AppUser user) {
        String passwordHash = user.getPasswordHash() != null ? user.getPasswordHash() : "";
        return User.builder()
                .username(user.getEmail())
                .password(passwordHash)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .disabled(!user.isActive())
                .build();
    }
}
