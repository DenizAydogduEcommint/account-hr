package com.ecommint.accounthr.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS yapılandırması. İzinli origin'ler artık koda gömülü DEĞİLDİR;
 * {@link CorsProperties} ({@code app.cors.allowed-origins}) üzerinden gelir.
 * Local profil varsayılanı {@code http://localhost:4200} (Angular dev sunucusu);
 * staging/prod {@code APP_CORS_ALLOWED_ORIGINS} env'inden okur.
 *
 * <p>{@code allowCredentials(true)} korunur; bununla joker ({@code *}) origin kullanılamaz,
 * bu yüzden somut (non-{@code *}) origin listesi kullanılır.
 */
@Configuration
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
