package com.ecommint.accounthr.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ecommint.accounthr.service.storage.DuplicateFileException;
import com.ecommint.accounthr.service.storage.StorageException;
import com.ecommint.accounthr.service.storage.StoragePathTraversalException;

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

    // --- E1-04 dosya depolama ---

    /** Mükerrer dosya (aynı provider+invoice_no veya aynı SHA-256) → 409 CONFLICT. */
    @ExceptionHandler(DuplicateFileException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateFile(DuplicateFileException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "DUPLICATE_FILE", "message", ex.getMessage()));
    }

    /** Path traversal / kök dışı yol → 400 BAD_REQUEST. */
    @ExceptionHandler(StoragePathTraversalException.class)
    public ResponseEntity<Map<String, String>> handlePathTraversal(StoragePathTraversalException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_PATH", "message", ex.getMessage()));
    }

    /** Diğer depolama hataları (geçersiz girdi, I/O) → 400 BAD_REQUEST. */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> handleStorage(StorageException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "STORAGE_ERROR", "message", ex.getMessage()));
    }
}
