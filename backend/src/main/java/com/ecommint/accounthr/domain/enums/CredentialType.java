package com.ecommint.accounthr.domain.enums;

/**
 * Servis credential tipi (E1-05). İleride otomatik fatura indirme (E5) için
 * panel login bilgileri / API key'ler burada sınıflandırılır.
 */
public enum CredentialType {
    /** Servis paneline giriş için kullanıcı adı + parola. */
    PANEL_LOGIN,
    /** Programatik erişim için API anahtarı / token. */
    API_KEY,
    /** Diğer hassas erişim bilgileri. */
    OTHER
}
