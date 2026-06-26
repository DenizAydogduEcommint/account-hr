package com.ecommint.accounthr.dto.service;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir servisin fatura iletişim kaydının dış gösterimi (E3-02).
 *
 * <p>{@link com.ecommint.accounthr.domain.ServiceContact} entity'sinin temiz alt
 * kümesi: e-posta, kaynak ve birincil (primary) bayrağı. E6 hatırlatma maillerinin
 * kime gideceğini bu liste belirler.
 */
@Schema(description = "Servis fatura iletişim kaydı (e-posta + kaynak + primary)")
public record ServiceContactDto(
        @Schema(description = "Fatura e-postası (tekil adres)", example = "accounting@e-commint.com")
        String email,
        @Schema(description = "Faturanın geldiği/yönetildiği kaynak metni",
                example = "E-posta", nullable = true)
        String source,
        @Schema(description = "Birincil iletişim mi", example = "true")
        boolean primary) {
}
