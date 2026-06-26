package com.ecommint.accounthr.service;

import java.time.LocalDateTime;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.RefreshToken;
import com.ecommint.accounthr.dto.auth.AuthResponse;
import com.ecommint.accounthr.dto.auth.UserResponse;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.RefreshTokenRepository;
import com.ecommint.accounthr.security.JwtService;

/**
 * Kimlik doğrulama iş mantığı: login (kimlik doğrula + token üret),
 * refresh (doğrula + rotate), logout (iptal), me (mevcut kullanıcı).
 *
 * Refresh token'lar opaque'tir; DB'de SHA-256 hash'i saklanır, her refresh'te
 * eski token iptal edilip yenisi üretilir (rotation).
 */
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** E-posta + parola doğrula; access + refresh token üret. Hatalı kimlikte BadCredentialsException. */
    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("E-posta veya parola hatalı."));

        if (!user.isActive()
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("E-posta veya parola hatalı.");
        }

        return issueTokens(user);
    }

    /**
     * Refresh token'ı doğrula ve rotate et: eski token iptal edilir, yeni
     * access + refresh üretilir. Geçersiz/expired/iptal edilmişse BadCredentialsException.
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token geçersiz."));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadCredentialsException("Refresh token geçersiz veya süresi dolmuş.");
        }

        // Rotation (ATOMİK): token'ı yalnızca hâlâ aktifse iptal et. Eşzamanlı iki
        // refresh aynı token'la geldiğinde, ikisi de yukarıdaki revoked==false
        // kontrolünü geçebilir; gerçek iptal tek bir UPDATE ... where revoked=false
        // ile yapılır ve yalnızca BİR çağrı 1 satır günceller. Diğeri 0 alır → 401.
        int revoked = refreshTokenRepository.revokeIfActive(hash);
        if (revoked != 1) {
            throw new BadCredentialsException("Refresh token geçersiz veya süresi dolmuş.");
        }

        AppUser user = stored.getUser();
        if (!user.isActive()) {
            throw new BadCredentialsException("Kullanıcı pasif.");
        }
        return issueTokens(user);
    }

    /**
     * Verilen refresh token'ı iptal et (logout). ATOMİK tek-UPDATE ({@code revokeIfActive})
     * kullanır — {@code refresh()} ile aynı desen. Dönen sayı yok sayılır: zaten iptal
     * edilmiş/bulunamayan token 0 satır günceller → idempotent no-op (çift logout güvenli).
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.revokeIfActive(hash);
    }

    /** Mevcut kullanıcının bilgisi (/api/auth/me). */
    @Transactional(readOnly = true)
    public UserResponse me(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Kullanıcı bulunamadı."));
        return UserResponse.from(user);
    }

    /** Access JWT + yeni opaque refresh token (DB'de hash'i saklanır) üret. */
    private AuthResponse issueTokens(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);

        String rawRefresh = jwtService.generateRefreshTokenValue();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(jwtService.hashRefreshToken(rawRefresh));
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTtlSeconds()));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);

        return new AuthResponse(
                accessToken,
                rawRefresh,
                TOKEN_TYPE,
                jwtService.getAccessTtlSeconds(),
                UserResponse.from(user));
    }
}
