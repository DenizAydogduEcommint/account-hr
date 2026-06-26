package com.ecommint.accounthr.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS yapılandırması ({@code app.cors.*}). İzinli origin listesi artık koda gömülü
 * DEĞİLDİR; {@code app.cors.allowed-origins} ile bağlanır. Local profil varsayılanı
 * Angular dev sunucusudur ({@code http://localhost:4200}); staging/prod
 * {@code APP_CORS_ALLOWED_ORIGINS} env'inden okur.
 *
 * <p>{@code allowCredentials(true)} ile birlikte joker ({@code *}) origin kullanılamaz;
 * bu yüzden liste her zaman somut origin'lerden oluşmalıdır (bkz. CorsConfig).
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** İzinli CORS origin'leri (somut, joker değil). Varsayılan: localhost:4200. */
    private List<String> allowedOrigins = List.of("http://localhost:4200");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
