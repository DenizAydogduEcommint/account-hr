package com.ecommint.accounthr.dto.service;

import java.math.BigDecimal;
import java.util.List;

import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceSource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Servis oluştur/güncelle isteği (E3-02) — Servisler ekranı ekle/düzenle formu.
 *
 * <p>{@code name} zorunludur. {@code providerName} resolve-or-create ile çözülür
 * (E2-02 mantığı): aynı isimli sağlayıcı varsa yeniden kullanılır, yoksa oluşturulur.
 * {@code cardLast4} tanımlı bir karta ({@code ****3800 / ****3909 / ****9164}) eşlenir;
 * eşleşmezse null bırakılır. {@code contacts} fatura iletişim kayıtlarıdır — her
 * birinin e-postası zorunlu ve geçerli olmalıdır (virgüllü çoklu adres destekli).
 */
@Schema(description = "Servis oluştur/güncelle isteği — Servisler ekranı ekle/düzenle formu")
public record ServiceRequest(
        @Schema(description = "Servis adı (zorunlu)", example = "Claude AI")
        @NotBlank(message = "must not be blank")
        String name,

        @Schema(description = "Fatura kesen firma adı (resolve-or-create)", example = "Anthropic",
                nullable = true)
        String providerName,

        @Schema(description = "Varsayılan kartın son 4 hanesi", example = "3800", nullable = true)
        String cardLast4,

        @Schema(example = "MONTHLY", nullable = true) Frequency frequency,

        @Schema(example = "YES", nullable = true) ActiveState activeState,

        @Schema(description = "Servisin görüldüğü/beklendiği aylar (virgüllü)",
                example = "2026-01, 2026-02", nullable = true)
        String activeMonths,

        @Schema(description = "Yaklaşık aylık TL tutarı", example = "1200.00", nullable = true)
        BigDecimal approxAmountTry,

        @Schema(description = "Operasyonel toplama dahil olmayan bilgi-amaçlı kalem mi",
                example = "false", nullable = true)
        Boolean informational,

        @Schema(example = "EMAIL", nullable = true) InvoiceSource invoiceSource,

        @Schema(description = "Kullanım amacı", nullable = true) String purpose,

        @Schema(description = "Özel notlar", nullable = true) String notes,

        @Schema(description = "Fatura iletişim kayıtları (e-posta zorunlu + kaynak + primary)")
        @Valid
        List<ServiceContactRequest> contacts) {
}
