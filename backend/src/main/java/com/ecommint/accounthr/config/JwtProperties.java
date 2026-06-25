package com.ecommint.accounthr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT yapılandırması (E1-03 / E1-05). {@code app.jwt.*} özelliklerini tipli olarak bağlar.
 *
 * <p>Önceden {@code JwtService} bu değerleri tek tek {@code @Value} ile okuyordu; E1-05'te
 * {@link StorageProperties} desenini izleyen bu props sınıfına taşındı. Secret ve TTL'ler
 * profil bazlı env değişkenleriyle override edilebilir (bkz. {@code application*.yml}).
 *
 * <p>Secret HS256 imzalama için en az 256-bit (32 byte) olmalıdır; PROD'da
 * {@code APP_JWT_SECRET} mutlaka set edilmelidir (repodaki dev varsayılanı KULLANILMAZ).
 */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS256 imzalama secret'ı (UTF-8, en az 32 byte). */
    private String secret;

    /** Access token ömrü (saniye). */
    private long accessTtl;

    /** Refresh token ömrü (saniye). */
    private long refreshTtl;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTtl() {
        return accessTtl;
    }

    public void setAccessTtl(long accessTtl) {
        this.accessTtl = accessTtl;
    }

    public long getRefreshTtl() {
        return refreshTtl;
    }

    public void setRefreshTtl(long refreshTtl) {
        this.refreshTtl = refreshTtl;
    }
}
