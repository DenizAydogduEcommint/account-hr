package com.ecommint.accounthr.dto.team;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Takım seçici (picker) için hafif gösterim — yalnızca id + ad.
 *
 * <p>E3-06 elle harcama formu, "Kullanan Takım" açılır menüsünü doldurmak için
 * {@code GET /api/v1/teams} üzerinden bu listeyi çeker.
 */
@Schema(description = "Takım seçici öğesi (id + ad).")
public record TeamOption(
        @Schema(example = "3") Long id,
        @Schema(example = "Backend") String name) {
}
