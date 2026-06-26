package com.ecommint.accounthr.dto.service;

import com.ecommint.accounthr.validation.EmailList;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Servis oluştur/güncelle isteğindeki tek bir fatura iletişim kaydı (E3-02).
 *
 * <p>{@code email} zorunludur ve geçerli e-posta formatında olmalıdır. E6 çoklu
 * alıcı ihtiyacı için <b>virgülle ayrılmış birden çok adres</b> desteklenir
 * ({@link EmailList}) — her adres ayrı ayrı doğrulanır.
 */
@Schema(description = "Servis fatura iletişim kaydı isteği (e-posta zorunlu, virgüllü çoklu adres destekli)")
public record ServiceContactRequest(
        @Schema(description = "Fatura e-postası. Virgülle birden çok adres olabilir.",
                example = "accounting@e-commint.com, finance@e-commint.com")
        @NotBlank(message = "must not be blank")
        @EmailList
        String email,

        @Schema(description = "Faturanın geldiği/yönetildiği kaynak metni",
                example = "E-posta", nullable = true)
        String source,

        @Schema(description = "Birincil iletişim mi (yoksa false)", example = "true", nullable = true)
        Boolean primary) {
}
