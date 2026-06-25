package com.ecommint.accounthr.service.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.ecommint.accounthr.config.CredentialEncryptionProperties;

/**
 * Uygulama seviyesi credential şifreleme servisi (E1-05): AES-256-GCM.
 *
 * <p>Servis panel parolaları / API key gibi hassas değerler DB'de DÜZ METİN saklanamaz.
 * Bu servis bunları master key ile şifreler/çözer. Master key
 * {@link CredentialEncryptionProperties} üzerinden (base64 kodlu 256-bit) gelir.
 *
 * <p>Şifreleme formatı: her {@link #encrypt(String)} çağrısında rastgele 12 byte IV
 * üretilir; çıktı {@code base64(IV || ciphertext || GCM-tag)} olur. GCM tag (16 byte)
 * ciphertext'in sonuna otomatik eklenir, böylece kurcalama (tampering) çözme sırasında
 * yakalanır ({@link AEADBadTagException} → {@link EncryptionException}).
 *
 * <p>Anahtar eksik/yanlış uzunluktaysa servis oluşturulurken FAIL-FAST yapılır:
 * uygulama açılışında net bir hata ile durur (sessizce zayıf şifreleme yapılmaz).
 *
 * <p>Secret yönetimi seam'i: bugün anahtar yalnızca env/.env'den okunur. İleride
 * Vault/cloud secret manager eklenirse, çözülmüş base64 anahtarı sağlayan kaynak
 * değişir; bu sınıfın arayüzü ({@code encrypt/decrypt}) sabit kalır.
 */
@Service
public class EncryptionService {

    /** AES-256 için anahtar uzunluğu (byte). */
    private static final int KEY_LENGTH_BYTES = 32;

    /** GCM önerilen IV uzunluğu (byte). */
    private static final int IV_LENGTH_BYTES = 12;

    /** GCM authentication tag uzunluğu (bit). */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(CredentialEncryptionProperties properties) {
        String masterKey = properties.getMasterKey();
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "Credential master key tanımlı değil: app.crypto.master-key (env CREDENTIAL_MASTER_KEY) "
                            + "set edilmeli. Base64 kodlu 256-bit anahtar üretmek için: "
                            + "openssl rand -base64 32");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(masterKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Credential master key geçerli base64 değil (app.crypto.master-key).", e);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "Credential master key uzunluğu hatalı: " + decoded.length
                            + " byte. AES-256-GCM için tam olarak " + KEY_LENGTH_BYTES
                            + " byte (256-bit) base64 anahtar gerekir.");
        }
        this.keyBytes = decoded;
    }

    /**
     * Düz metni AES-256-GCM ile şifreler. Çıktı {@code base64(IV || ciphertext || tag)}.
     * Aynı girdi her çağrıda farklı IV → farklı ciphertext üretir.
     *
     * @param plaintext şifrelenecek metin; {@code null} ise {@code null} döner
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Hata mesajına düz metni ASLA koyma.
            throw new EncryptionException("Şifreleme başarısız.", e);
        }
    }

    /**
     * {@link #encrypt(String)} çıktısını çözer. Kurcalanmış/bozuk ciphertext GCM tag
     * doğrulamasında yakalanır ve {@link EncryptionException} fırlatılır.
     *
     * @param encoded {@code base64(IV || ciphertext || tag)}; {@code null} ise {@code null}
     */
    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_LENGTH_BYTES) {
                throw new EncryptionException("Şifreli değer geçersiz (çok kısa).");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            // AEADBadTagException dâhil her çözme hatası burada normalize edilir.
            throw new EncryptionException("Çözme başarısız (anahtar yanlış veya veri kurcalanmış).", e);
        }
    }
}
