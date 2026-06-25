package com.ecommint.accounthr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Tek bir refresh token'ı ATOMİK olarak iptal eder (rotation race koruması).
     * Yalnızca henüz iptal edilmemiş satırı günceller; dönen değer güncellenen satır
     * sayısıdır (0 = zaten iptal edilmiş/yok, 1 = bu çağrı iptal etti). Eşzamanlı iki
     * refresh'ten yalnızca biri 1 alır; diğeri 0 alıp 401 ile reddedilir.
     */
    @Modifying
    @Transactional
    @Query("update RefreshToken r set r.revoked = true where r.tokenHash = :h and r.revoked = false")
    int revokeIfActive(@Param("h") String tokenHash);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    /** Belirli bir kullanıcının tüm aktif refresh token'larını iptal et (global logout için). */
    @Modifying
    @Transactional
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);
}
