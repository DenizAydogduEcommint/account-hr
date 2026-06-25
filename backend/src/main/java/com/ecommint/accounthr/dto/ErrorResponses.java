package com.ecommint.accounthr.dto;

import java.time.Instant;
import java.util.List;

import org.slf4j.MDC;

import com.ecommint.accounthr.logging.CorrelationIdFilter;

/**
 * {@link ErrorResponse} üretmek için ortak fabrika. GlobalExceptionHandler ve
 * güvenlik filtre zincirindeki 401/403 işleyicileri buradan üretir; böylece tek
 * bir tutarlı hata şekli garanti edilir.
 */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    /** MDC'deki ({@code correlationId}) trace id; yoksa {@code null}. */
    public static String currentTraceId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return of(status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path,
            List<ErrorResponse.FieldError> fieldErrors) {
        return new ErrorResponse(
                Instant.now(),
                status,
                error,
                message,
                path,
                currentTraceId(),
                fieldErrors);
    }
}
