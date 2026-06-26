package com.ecommint.accounthr.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.dto.team.TeamOption;
import com.ecommint.accounthr.repository.TeamRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Takım seçici (picker) kaynağı (E3-06).
 *
 * <p>Elle harcama formundaki "Kullanan Takım" açılır menüsünü doldurmak için
 * takımların hafif {@code [{ id, name }]} listesini ada göre sıralı döner. Salt
 * okunur; herhangi bir kimliği doğrulanmış kullanıcıya açık.
 */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Takım seçici kaynağı (id + ad)")
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamRepository teamRepository;

    public TeamController(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /** Tüm takımları ada göre alfabetik sıralı, hafif {@link TeamOption} listesi olarak döner. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING','TEAM_MEMBER')")
    @Operation(summary = "Takımları listele (picker)",
            description = "Ada göre sıralı [{ id, name }] listesi. Kimlik doğrulama gerektirir.")
    public List<TeamOption> list() {
        return teamRepository.findAllByOrderByNameAsc().stream()
                .map(t -> new TeamOption(t.getId(), t.getName()))
                .toList();
    }
}
