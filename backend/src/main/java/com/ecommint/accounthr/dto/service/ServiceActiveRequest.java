package com.ecommint.accounthr.dto.service;

import com.ecommint.accounthr.domain.enums.ActiveState;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Servis aktiflik durumunu değiştirme isteği (E3-02).
 *
 * <p>Sert silme (hard DELETE) YOKTUR; geçmiş veriyle ilişki bozulmasın diye servis
 * pasifleştirilir ({@code activeState = NO}) ya da yeniden aktifleştirilir.
 */
@Schema(description = "Servis aktiflik durumu değiştirme isteği (silme yerine pasifleştirme)")
public record ServiceActiveRequest(
        @Schema(description = "Yeni aktiflik durumu", example = "NO")
        @NotNull(message = "must not be null")
        ActiveState activeState) {
}
