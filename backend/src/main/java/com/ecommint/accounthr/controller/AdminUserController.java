package com.ecommint.accounthr.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.dto.admin.AdminUserResponse;
import com.ecommint.accounthr.dto.admin.ChangeActiveRequest;
import com.ecommint.accounthr.dto.admin.ChangePasswordRequest;
import com.ecommint.accounthr.dto.admin.ChangeRoleRequest;
import com.ecommint.accounthr.dto.admin.CreateUserRequest;
import com.ecommint.accounthr.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Admin kullanıcı yönetimi (backoffice) uçları (E1-08). TÜM uçlar yalnızca ADMIN
 * rolüne açıktır ({@code @PreAuthorize("hasRole('ADMIN')")}); ACCOUNTING/TEAM_MEMBER
 * → 403, kimlik doğrulanmamış → 401.
 *
 * <p>Güvenlik: yanıtlarda parola hash'i ASLA dönmez ({@link AdminUserResponse}).
 * Parola değişimi hedefin refresh token'larını iptal eder. Son aktif ADMIN'in
 * rolü düşürülemez/pasifleştirilemez (409 LAST_ADMIN). Kullanıcı DELETE ucu YOKTUR
 * (yalnızca pasifleştirme).
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Admin kullanıcı yönetimi (yalnızca ADMIN)")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** Tüm kullanıcıları e-postaya göre sıralı listeler (parola hash'i hariç). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Kullanıcıları listele", description = "E-postaya göre sıralı; parola hash'i içermez.")
    public List<AdminUserResponse> list() {
        return adminUserService.list();
    }

    /** Yeni kullanıcı oluşturur → 201. Mükerrer e-posta → 409, parola < 8 → 400. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kullanıcı oluştur", description = "E-posta benzersiz; parola BCrypt ile hash'lenir.")
    public AdminUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return adminUserService.create(request);
    }

    /** Parolayı değiştirir ve hedefin refresh token'larını iptal eder → 204. Bilinmeyen id → 404. */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Parola değiştir",
            description = "BCrypt ile yeniden hash'ler ve hedefin tüm refresh token'larını iptal eder.")
    public ResponseEntity<Void> changePassword(@PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request) {
        adminUserService.changePassword(id, request.password());
        return ResponseEntity.noContent().build();
    }

    /** Rolü değiştirir → 200. Son aktif ADMIN düşürülemez (409). Bilinmeyen id → 404. */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rol değiştir", description = "Son aktif ADMIN'in rolü düşürülemez (409 LAST_ADMIN).")
    public AdminUserResponse changeRole(@PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return adminUserService.changeRole(id, request.role());
    }

    /** Aktiflik durumunu değiştirir → 200. Son aktif ADMIN pasifleştirilemez (409). Bilinmeyen id → 404. */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aktiflik değiştir", description = "Son aktif ADMIN pasifleştirilemez (409 LAST_ADMIN).")
    public AdminUserResponse changeActive(@PathVariable Long id,
            @Valid @RequestBody ChangeActiveRequest request) {
        return adminUserService.changeActive(id, request.active());
    }
}
