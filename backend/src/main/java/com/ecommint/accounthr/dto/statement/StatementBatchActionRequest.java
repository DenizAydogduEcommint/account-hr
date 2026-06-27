package com.ecommint.accounthr.dto.statement;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/statements/confirm} ve {@code .../discard} istek gövdesi (E4-01).
 *
 * @param batchRef onaylanacak/reddedilecek batch referansı (= sha256)
 */
public record StatementBatchActionRequest(
        @NotBlank String batchRef) {
}
