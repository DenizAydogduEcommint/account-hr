package com.ecommint.accounthr.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tutarlı hata formatı: {"error":<CODE>,"message":<text>}.
 * - Geçersiz kimlik / kimlik doğrulama hatası → 401 UNAUTHORIZED
 * - Doğrulama hatası (@Valid) → 400 VALIDATION_ERROR
 *
 * NOT: Filtre zincirindeki 401/403 (token yok / yetki yok) ilgili
 * AuthenticationEntryPoint / AccessDeniedHandler tarafından üretilir; bu advice
 * controller'a ulaşan iş-mantığı istisnalarını karşılar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ BadCredentialsException.class, AuthenticationException.class })
    public ResponseEntity<Map<String, String>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "UNAUTHORIZED", "message", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Geçersiz istek.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "VALIDATION_ERROR", "message", message));
    }
}
