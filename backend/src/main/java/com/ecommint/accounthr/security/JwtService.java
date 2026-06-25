package com.ecommint.accounthr.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.ecommint.accounthr.config.JwtProperties;
import com.ecommint.accounthr.domain.AppUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT access token üretimi/doğrulaması ve opaque refresh token üretimi.
 *
 * - Access token: HS256 imzalı JWT. Claim'ler: sub=userId, email, role.
 * - Refresh token: opaque (rastgele 256-bit), DB'de SHA-256 hash'i saklanır.
 *   JWT DEĞİLDİR; iptal edilebilirlik için DB'de tutulur.
 *
 * Secret ve TTL'ler config'ten (app.jwt.*) gelir, env ile override edilebilir.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    /** HS256 için minimum secret uzunluğu (byte): 256-bit = 32 byte. */
    private static final int MIN_SECRET_BYTES = 32;

    public JwtService(JwtProperties properties) {
        // FAIL-FAST: secret REPODA tutulmaz; eksik/kısa ise (staging/prod'da
        // APP_JWT_SECRET set edilmemişse) uygulama açılışta net hata ile durur —
        // EncryptionService master-key doğrulamasıyla aynı desen. Zayıf/forge'lanabilir
        // imza ile sessizce boot etmek YASAK.
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret tanımlı değil: app.jwt.secret (env APP_JWT_SECRET) set edilmeli. "
                            + "HS256 için en az " + MIN_SECRET_BYTES + " byte (UTF-8) gerekir. "
                            + "Üretmek için: openssl rand -base64 48");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret çok kısa: " + secretBytes.length + " byte. HS256 için en az "
                            + MIN_SECRET_BYTES + " byte (256-bit) gerekir (app.jwt.secret).");
        }
        // HS256 için secret en az 256-bit (32 byte) olmalı.
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtlSeconds = properties.getAccessTtl();
        this.refreshTtlSeconds = properties.getRefreshTtl();
    }

    /** Access token TTL'i saniye cinsinden (login/refresh yanıtındaki expiresIn). */
    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    /** Kullanıcı için imzalı access JWT üret. */
    public String generateAccessToken(AppUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTtlSeconds * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Opaque refresh token üret (rastgele 256-bit, URL-safe base64).
     * Düz değer istemciye döner; DB'de yalnızca SHA-256 hash'i saklanır.
     */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Refresh token'ın SHA-256 hex digesti (DB'de saklanan değer, 64 karakter). */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 bulunamadı", e);
        }
    }

    /** Access token'ı doğrula ve claim'leri döndür; geçersiz/expired ise JwtException fırlatır. */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
