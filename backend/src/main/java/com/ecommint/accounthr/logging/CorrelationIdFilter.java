package com.ecommint.accounthr.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Her isteğe bir correlation (request) id atayan filtre (E1-05).
 *
 * <p>Gelen istekte {@code X-Request-Id} başlığı varsa onu kullanır; yoksa rastgele bir
 * UUID üretir. Değeri SLF4J {@link MDC}'ye {@value #MDC_KEY} anahtarıyla koyar (böylece
 * yapılandırılmış/desenli loglara otomatik düşer) ve yanıta {@code X-Request-Id} olarak
 * geri yazar. İstek bittiğinde MDC mutlaka temizlenir (thread-pool sızıntısını önler).
 *
 * <p>JWT filtresinden ÖNCE çalışır ({@link Ordered#HIGHEST_PRECEDENCE}) ki auth dâhil
 * tüm log satırları correlation id taşısın.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Request-Id";

    /** İstemci-sağlanan correlation id için izin verilen uzunluk + karakter sınırı. */
    static final int MAX_LENGTH = 64;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]+");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // İstemci X-Request-Id'sini doğrula (log injection/pollution savunması): boş/eksik,
        // 64 karakterden uzun ya da [A-Za-z0-9-] dışı karakter içeren değer reddedilip yerine
        // taze bir UUID üretilir. Aksi halde gelen değer aynen kullanılır.
        String correlationId = request.getHeader(HEADER);
        if (!isValid(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Gelen değer non-blank, ≤ {@value #MAX_LENGTH} karakter ve yalnızca [A-Za-z0-9-] ise geçerli. */
    private static boolean isValid(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_LENGTH
                && SAFE_ID.matcher(value).matches();
    }
}
