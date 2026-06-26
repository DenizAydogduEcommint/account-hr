package com.ecommint.accounthr.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ecommint.accounthr.dto.ErrorResponse;
import com.ecommint.accounthr.dto.ErrorResponses;
import com.ecommint.accounthr.service.InvalidExpenseRequestException;
import com.ecommint.accounthr.service.ResourceNotFoundException;
import com.ecommint.accounthr.service.drive.DriveSyncException;
import com.ecommint.accounthr.service.drive.DriveSyncValidationException;
import com.ecommint.accounthr.service.importer.ExcelImportException;
import com.ecommint.accounthr.service.importer.InvoiceFileImportException;
import com.ecommint.accounthr.service.storage.DuplicateFileException;
import com.ecommint.accounthr.service.storage.StorageException;
import com.ecommint.accounthr.service.storage.StorageIOException;
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
 *   <li>{@code DriveSyncValidationException} → 400 DRIVE_REQUEST_INVALID (çağıran girdisi: geçersiz dosya adı)</li>
 *   <li>{@code DriveSyncException} → 502 DRIVE_SYNC_ERROR (dış bağımlılık: rclone/Drive)</li>
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /**
     * Geçersiz enum/tip query parametresi (ör. {@code ?active=GARBAGE} veya
     * {@code ?frequency=GARBAGE}) → 400 VALIDATION_ERROR. Spring bunu
     * {@link MethodArgumentTypeMismatchException} olarak fırlatır; yakalanmazsa
     * genel handler bunu yanıltıcı bir 500'e çevirirdi (E3-02 düzeltmesi).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String value = ex.getValue() != null ? ex.getValue().toString() : "null";
        String message = "Geçersiz değer '" + value + "' — parametre '" + ex.getName() + "'";
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request, null);
    }

    /**
     * Eksik/bozuk multipart isteği (örn. POST /files'a "file" part'ı olmadan istek) →
     * istemci hatasıdır, 400 döner; genel handler aksi halde yanıltıcı 500 üretirdi.
     */
    @ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
    public ResponseEntity<ErrorResponse> handleMultipart(
            Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART",
                "Geçersiz veya eksik dosya yükleme isteği.", request, null);
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

    /**
     * Mükerrer dosya (aynı provider+invoice_no veya aynı SHA-256) → 409 CONFLICT.
     *
     * <p>{@code ex.getMessage()} mutlak dosya yolu ve/veya SHA-256 içerebilir; bunlar
     * YANITA KONMAZ (bilgi sızıntısı). Sabit bir mesaj döner, orijinali traceId ile
     * yalnızca sunucu loguna yazılır.
     */
    @ExceptionHandler(DuplicateFileException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateFile(DuplicateFileException ex, HttpServletRequest request) {
        logSanitized("DUPLICATE_FILE", ex, request);
        return build(HttpStatus.CONFLICT, "DUPLICATE_FILE",
                "A duplicate file already exists.", request, null);
    }

    /**
     * Veritabanı bütünlük ihlali (ör. eşzamanlı aynı-isim sağlayıcı oluşturma →
     * unique-constraint çakışması) → 409 CONFLICT backstop. Asıl düzeltme servis
     * katmanındaki idempotent resolve-or-create'tir (saveAndFlush + re-query);
     * bu handler yine de bütünlük hatalarının 500'e düşmesini engeller (E3-02).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "The request conflicts with the current state of a resource.", request, null);
    }

    /**
     * Path traversal / kök dışı yol → 400 BAD_REQUEST. Mesaj mutlak dosya yolu içerebilir;
     * sabit mesaj döner, orijinali logla.
     */
    @ExceptionHandler(StoragePathTraversalException.class)
    public ResponseEntity<ErrorResponse> handlePathTraversal(
            StoragePathTraversalException ex, HttpServletRequest request) {
        logSanitized("INVALID_PATH", ex, request);
        return build(HttpStatus.BAD_REQUEST, "INVALID_PATH", "Invalid file path.", request, null);
    }

    /**
     * Sunucu tarafı depolama I/O arızası (disk dolu, taşıma/temp-create başarısız) →
     * 503 SERVICE_UNAVAILABLE. Bu çağıran girdisi hatası DEĞİLDİR. {@link StorageException}
     * alt türü olduğundan Spring bu daha-özgül handler'ı 400'lük {@link #handleStorage}
     * yerine seçer. Mesaj mutlak yol içerebilir → sabit mesaj döner, orijinali logla.
     */
    @ExceptionHandler(StorageIOException.class)
    public ResponseEntity<ErrorResponse> handleStorageIO(StorageIOException ex, HttpServletRequest request) {
        logSanitized("STORAGE_IO_ERROR", ex, request);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_IO_ERROR",
                "File storage operation failed.", request, null);
    }

    /**
     * Diğer depolama hataları (geçersiz GİRDİ doğrulaması: boş serviceName, null tarih) →
     * 400 BAD_REQUEST. I/O arızaları {@link StorageIOException} ile yukarıda 503'e ayrıldı.
     * Mesaj mutlak yol içerebilir → sabit mesaj döner, orijinali logla.
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(StorageException ex, HttpServletRequest request) {
        logSanitized("STORAGE_ERROR", ex, request);
        return build(HttpStatus.BAD_REQUEST, "STORAGE_ERROR",
                "File storage operation failed.", request, null);
    }

    /** Excel import hatası (okuma/parse) → 400 BAD_REQUEST. */
    @ExceptionHandler(ExcelImportException.class)
    public ResponseEntity<ErrorResponse> handleExcelImport(ExcelImportException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "IMPORT_ERROR", ex.getMessage(), request, null);
    }

    /**
     * Fatura dosyası tarama/kopyalama hatası (E2-03): caller/validation hataları
     * (sourceDir izinli kök dışı, dizin değil) ve I/O hataları → 400 BAD_REQUEST.
     * {@link ExcelImportException} ile aynı {@code IMPORT_ERROR} şekli; aksi halde
     * genel handler bunu 500'e çevirirdi.
     */
    @ExceptionHandler(InvoiceFileImportException.class)
    public ResponseEntity<ErrorResponse> handleInvoiceFileImport(
            InvoiceFileImportException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "IMPORT_ERROR", ex.getMessage(), request, null);
    }

    /**
     * Drive senkron köprüsü ÇAĞIRAN GİRDİSİ hatası (E2-06): geçersiz/traversal dosya adı
     * → 400 BAD_REQUEST. Bu bir dış bağımlılık (rclone/Drive) hatası DEĞİLDİR, dolayısıyla
     * 502 olamaz. {@link DriveSyncException}'ın alt sınıfıdır; Spring en özgül handler'ı
     * seçtiği için bu 400 eşlemesi aşağıdaki 502 eşlemesinden ÖNCE eşleşir.
     */
    @ExceptionHandler(DriveSyncValidationException.class)
    public ResponseEntity<ErrorResponse> handleDriveSyncValidation(
            DriveSyncValidationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "DRIVE_REQUEST_INVALID", ex.getMessage(), request, null);
    }

    /**
     * Drive {@code waiting/} senkron köprüsü (E2-06) hatası: rclone yok/başarısız/zaman
     * aşımı veya Drive erişilemez → 502 BAD_GATEWAY. Bir dış bağımlılık (rclone/Drive)
     * sorunudur; uygulamanın kendi 500 hatası DEĞİLDİR. Ham stderr/stack mesaja konmaz.
     */
    @ExceptionHandler(DriveSyncException.class)
    public ResponseEntity<ErrorResponse> handleDriveSync(DriveSyncException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_GATEWAY, "DRIVE_SYNC_ERROR", ex.getMessage(), request, null);
    }

    /**
     * Dashboard {@code month} parametresi biçimsiz (YYYY-MM değil) → 400 BAD_REQUEST.
     * İyi-biçimli ama bilinmeyen ay (ör. 2026-09) buraya DÜŞMEZ; servis sıfır özet döner.
     */
    @ExceptionHandler(InvalidMonthException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMonth(InvalidMonthException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_MONTH", ex.getMessage(), request, null);
    }

    /** Bilinmeyen rota / statik kaynak yok → 404 NOT_FOUND. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request, null);
    }

    /**
     * Elle harcama satırı (E3-06) isteğinde veri-bağımlı geçersizlik (bilinmeyen
     * {@code cardLast4} / {@code usingTeamId}) → 400 VALIDATION_ERROR. Alan-bazı (format/null)
     * hatalar {@link MethodArgumentNotValidException} ile zaten aynı şekle eşlenir.
     */
    @ExceptionHandler(InvalidExpenseRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidExpenseRequest(
            InvalidExpenseRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request, null);
    }

    /** İstenen kaynak (ör. id ile servis) bulunamadı → 404 NOT_FOUND. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request, null);
    }

    /**
     * Var olan bir yola desteklenmeyen HTTP metoduyla istek (ör. {@code DELETE
     * /api/v1/services/{id}} — sert silme bilinçli olarak yoktur) → 405. Aksi halde
     * genel handler bunu yanıltıcı bir 500'e çevirirdi.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method not supported for this resource.", request, null);
    }

    /** Son güvenlik ağı: beklenmeyen hatalar → 500. Stack trace / sır SIZDIRMAZ. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = org.slf4j.MDC.get("correlationId");
        // Full stack to logs ONLY; HTTP response body never carries stack/secrets.
        log.error("Unhandled 500 [traceId={}] on {} {}", traceId, request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred.", request, null);
    }

    /**
     * Depolama hatalarının ORİJİNAL mesajını (mutlak yol / SHA-256 içerebilir) yalnızca
     * sunucu loguna yazar; HTTP yanıtına sabit, sızdırmayan bir mesaj döner.
     */
    private void logSanitized(String code, Exception ex, HttpServletRequest request) {
        String traceId = org.slf4j.MDC.get("correlationId");
        log.warn("{} [traceId={}] on {} {}: {}", code, traceId,
                request.getMethod(), request.getRequestURI(), ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            HttpServletRequest request, List<ErrorResponse.FieldError> fieldErrors) {
        ErrorResponse body = ErrorResponses.of(
                status.value(), code, message, request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
