package com.ecommint.accounthr.logging;

/**
 * Hassas değerleri loglara düşmeden önce maskeleyen yardımcı (E1-05).
 *
 * <p>Parola, parola hash'i, JWT/refresh token, şifreli credential secret, master key,
 * {@code Authorization} başlığı gibi değerler loglara DÜZ METİN olarak ASLA yazılmaz.
 * Bu sınıf, böyle bir değeri loglamak gerektiğinde kısaltılmış/maskeli bir temsil üretir.
 *
 * <p>Strateji: değer tamamen gizlenmez (debug'ı imkânsızlaştırmamak için) — uzunluğa
 * göre ya tümü {@code ****} olur (kısa değerler) ya da yalnızca son birkaç karakter
 * görünür. Hiçbir durumda gizli içeriğin anlamlı bir kısmı sızmaz.
 */
public final class LogMasker {

    private static final String FULL_MASK = "****";
    private static final int VISIBLE_SUFFIX = 4;

    private LogMasker() {
    }

    /**
     * Hassas bir değeri maskeler. {@code null} → {@code "null"}; kısa değerler tamamen
     * gizlenir; uzun değerlerde yalnızca son {@value #VISIBLE_SUFFIX} karakter görünür.
     */
    public static String mask(String value) {
        if (value == null) {
            return "null";
        }
        if (value.isEmpty()) {
            return FULL_MASK;
        }
        if (value.length() <= VISIBLE_SUFFIX * 2) {
            return FULL_MASK;
        }
        String suffix = value.substring(value.length() - VISIBLE_SUFFIX);
        return FULL_MASK + suffix;
    }

    /**
     * {@code Authorization} başlığını maskeler. {@code "Bearer <token>"} şemasında
     * şema adı korunur, token maskelenir. Diğer her şey tamamen maskelenir.
     */
    public static String maskAuthorizationHeader(String headerValue) {
        if (headerValue == null) {
            return "null";
        }
        int space = headerValue.indexOf(' ');
        if (space > 0) {
            String scheme = headerValue.substring(0, space);
            return scheme + " " + FULL_MASK;
        }
        return FULL_MASK;
    }
}
