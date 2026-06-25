package com.ecommint.accounthr.logging;

import java.io.IOException;
import java.util.UUID;

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

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
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
}
