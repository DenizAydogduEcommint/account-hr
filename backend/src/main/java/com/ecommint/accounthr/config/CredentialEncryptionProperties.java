package com.ecommint.accounthr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credential şifreleme yapılandırması (E1-05). {@code app.crypto.*} özelliklerini bağlar.
 *
 * <p>{@code masterKey}, servis panel parolaları / API key gibi hassas credential'ları
 * uygulama seviyesinde AES-256-GCM ile şifrelemek için kullanılan master anahtardır.
 * Değer {@code CREDENTIAL_MASTER_KEY} env değişkeninden gelir ve <b>base64 kodlu 256-bit
 * (32 byte) bir anahtar</b> olmalıdır.
 *
 * <p>Master key REPODA TUTULMAZ. Yalnızca local/test profillerinde, uygulamanın yerelde
 * boot olabilmesi için açıkça "yalnızca-dev" olarak işaretlenmiş bir varsayılan verilir
 * (bkz. {@code application-local.yml} / {@code application-test.yml}). Staging/prod'da
 * env'den (ileride secret manager'dan) gelmelidir; eksikse uygulama açılışta fail eder.
 *
 * <p>Secret yönetimi seam'i: bugün yalnızca env/.env desteklenir. İleride Vault/cloud
 * secret manager eklenirse anahtarın nasıl çözüldüğü {@code EncryptionService} arkasında
 * soyutlanır; bu props sınıfı yine "çözülmüş base64 anahtar" sözleşmesini korur.
 */
@ConfigurationProperties(prefix = "app.crypto")
public class CredentialEncryptionProperties {

    /** Base64 kodlu 256-bit (32 byte) AES master anahtarı. */
    private String masterKey;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }
}
