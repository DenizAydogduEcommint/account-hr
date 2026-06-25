package com.ecommint.accounthr.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ecommint.accounthr.config.JwtProperties;

/**
 * E1-05 fix: JWT signing secret FAIL-FAST doğrulaması (Spring context'siz birim test).
 * EncryptionService master-key doğrulamasıyla aynı desen.
 */
class JwtServiceTest {

    private static JwtProperties props(String secret) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setAccessTtl(900);
        p.setRefreshTtl(604800);
        return p;
    }

    @Test
    void blankSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(props("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }

    @Test
    void nullSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(props(null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tooShortSecretFailsFast() {
        // 31 byte (<32) — HS256 için yetersiz.
        assertThatThrownBy(() -> new JwtService(props("0123456789012345678901234567890")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("byte");
    }

    @Test
    void validSecretConstructsAndSigns() {
        JwtService svc = new JwtService(props("a-valid-dev-only-secret-min-32-bytes-long-xyz"));
        assertThat(svc.getAccessTtlSeconds()).isEqualTo(900);
        assertThat(svc.generateRefreshTokenValue()).isNotBlank();
    }
}
