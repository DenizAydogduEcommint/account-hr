package com.ecommint.accounthr.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ecommint.accounthr.dto.ErrorResponse;
import com.ecommint.accounthr.dto.ErrorResponses;
import com.ecommint.accounthr.service.storage.DuplicateFileException;
import com.ecommint.accounthr.service.storage.StorageException;
import com.ecommint.accounthr.service.storage.StoragePathTraversalException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Projenin tek standart hata formatını ({@link ErrorResponse}) üreten global
 * exception handler (E1-07).
 *
 * <p>Eşlemeler:
 * <ul>
 *   <li>{@code MethodArgumentNotValidException} / {@code ConstraintViolationException}
 *       → 400 VALIDATION_ERROR (+ alan bazlı {@code fieldErrors})</li>
 *   <li>{@code BadCredentialsException} / {@code AuthenticationException} → 401 UNAUTHORIZED</li>
 *   <li>{@code DuplicateFileException} → 409 DUPLICATE_FILE</li>
 *   <li>{@code StoragePathTraversalException} → 400 INVALID_PATH</li>
 *   <li>{@code StorageException} → 400 STORAGE_ERROR</li>
 *   <li>{@code NoResourceFoundException} → 404 NOT_FOUND</li>
 *   <li>diğer her şey → 500 INTERNAL_ERROR (stack/secret SIZDIRMAZ)</li>
 * </ul>
 *
 * <p>Mesajlar İngilizcedir (frontend Türkçeye çevirir); {@code traceId} MDC
 * {@code correlationId}'den okunur. Filtre zincirindeki 401/403 ({@code token yok /
 * yetki yok}) ilgili entry point / access denied handler tarafından AYNI şekille
 * üretilir.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value"))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Validation failed for one or more fields.", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(
                        v.getPropertyPath() != null ? v.getPropertyPath().toString() : "",
                        v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Validation failed for one or more fields.", request, fieldErrors);
    }

    @ExceptionHandler({ BadCredentialsException.class, AuthenticationException.class })
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required or credentials are invalid.", request, null);
    }

    /**
     * {@code @PreAuthorize} reddi (403). Bu advice ile YAKALAYIP gövde üretmiyoruz;
     * yeniden fırlatıyoruz ki Spring Security'nin ExceptionTranslationFilter'ı
     * {@code RestAccessDeniedHandler}'a yönlendirsin (403 için tek kaynak). Aksi
     * halde aşağıdaki genel {@code Exception} handler bunu 500'e çevirirdi.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    /** Mükerrer dosya (aynı provider+invoice_no veya aynı SHA-256) → 409 CONFLICT. */
    @ExceptionHandler(DuplicateFileException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateFile(DuplicateFileException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_FILE", ex.getMessage(), request, null);
    }

    /** Path traversal / kök dışı yol → 400 BAD_REQUEST. */
    @ExceptionHandler(StoragePathTraversalException.class)
    public ResponseEntity<ErrorResponse> handlePathTraversal(
            StoragePathTraversalException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PATH", ex.getMessage(), request, null);
    }

    /** Diğer depolama hataları (geçersiz girdi, I/O) → 400 BAD_REQUEST. */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "STORAGE_ERROR", ex.getMessage(), request, null);
    }

    /** Bilinmeyen rota / statik kaynak yok → 404 NOT_FOUND. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request, null);
    }

    /** Son güvenlik ağı: beklenmeyen hatalar → 500. Stack trace / sır SIZDIRMAZ. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {
        ErrorResponse body = ErrorResponses.of(
                status.value(), code, message, request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
