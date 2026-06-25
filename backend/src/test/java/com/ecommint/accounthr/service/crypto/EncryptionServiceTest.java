package com.ecommint.accounthr.service.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.ecommint.accounthr.config.CredentialEncryptionProperties;

/**
 * E1-05 AES-256-GCM şifreleme servisi birim testleri (Spring context'siz, saf birim).
 */
class EncryptionServiceTest {

    private static EncryptionService service() {
        CredentialEncryptionProperties props = new CredentialEncryptionProperties();
        // "test-fixed-master-key-32byte!!!!" base64'ü (32 byte / 256-bit).
        props.setMasterKey("dGVzdC1maXhlZC1tYXN0ZXIta2V5LTMyYnl0ZSEhISE=");
        return new EncryptionService(props);
    }

    @Test
    void encryptThenDecryptRoundTrips() {
        EncryptionService svc = service();
        String plaintext = "süper-gizli-panel-parolası-123";

        String encrypted = svc.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(svc.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void samePlaintextYieldsDifferentCiphertext() {
        EncryptionService svc = service();
        String plaintext = "aynı-girdi";

        String first = svc.encrypt(plaintext);
        String second = svc.encrypt(plaintext);

        // Rastgele IV → her şifrelemede farklı ciphertext.
        assertThat(first).isNotEqualTo(second);
        // Ama ikisi de aynı düz metne çözülür.
        assertThat(svc.decrypt(first)).isEqualTo(plaintext);
        assertThat(svc.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void tamperedCiphertextFailsToDecrypt() {
        EncryptionService svc = service();
        String encrypted = svc.encrypt("bütünlüğü-korunan-değer");

        // Son byte'ı boz (GCM tag doğrulaması bunu yakalamalı).
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> svc.decrypt(tampered))
                .isInstanceOf(EncryptionException.class);
    }

    @Test
    void nullRoundTripsAsNull() {
        EncryptionService svc = service();
        assertThat(svc.encrypt(null)).isNull();
        assertThat(svc.decrypt(null)).isNull();
    }

    @Test
    void missingMasterKeyFailsFast() {
        CredentialEncryptionProperties props = new CredentialEncryptionProperties();
        props.setMasterKey("");
        assertThatThrownBy(() -> new EncryptionService(props))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongLengthMasterKeyFailsFast() {
        CredentialEncryptionProperties props = new CredentialEncryptionProperties();
        // 16 byte (128-bit) — AES-256 için yetersiz.
        props.setMasterKey(Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(() -> new EncryptionService(props))
                .isInstanceOf(IllegalStateException.class);
    }
}
