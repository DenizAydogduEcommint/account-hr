package com.ecommint.accounthr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fatura dosya depolama yapılandırması (E1-04). `app.storage.*` özelliklerini bağlar.
 *
 * <p>{@code root} değeri {@code STORAGE_ROOT} env değişkeninden gelir; varsayılanı
 * {@code ${user.home}/account-hr-data/faturalar}'dır. Bu dizin REPO DIŞINDADIR ve
 * repoya commit EDİLMEZ. Drive aynası {@code expenses/faturalar} ile karıştırılmamalı —
 * bu servis o dizine asla dokunmaz.
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Faturaların yazıldığı kök dizin (mutlak yol). */
    private String root;

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }
}
