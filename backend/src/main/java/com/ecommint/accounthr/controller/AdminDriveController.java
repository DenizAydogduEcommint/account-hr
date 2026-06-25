package com.ecommint.accounthr.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.dto.drive.DriveSyncResult;
import com.ecommint.accounthr.service.drive.DriveSyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Yönetici Google Drive senkron uçları (E2-06).
 *
 * <p>{@code POST /api/v1/admin/drive/pull-waiting} — Drive {@code faturalar/waiting/}'i
 * lokale çeker (rclone {@code copy}, yön: remote → local) ve bu çağrıda inen yeni
 * dosyaları döndürür. MANUEL tetikleme; her zaman-açık bir {@code @Scheduled} job
 * YOKTUR (varsa {@code app.drive.scheduled-enabled} bayrağıyla korunur, varsayılan
 * kapalı). Yalnızca ADMIN.
 *
 * <p>Köprü kapalıysa ({@code app.drive.enabled=false}) temiz bir no-op sonucu
 * ({@code skipped=true}) döner; Drive erişilemez/rclone yoksa servis
 * {@link com.ecommint.accounthr.service.drive.DriveSyncException} fırlatır ve
 * {@link GlobalExceptionHandler} bunu 502 BAD_GATEWAY'e çevirir (asla 500 değil).
 */
@RestController
@RequestMapping("/api/v1/admin/drive")
@Tag(name = "Admin Drive", description = "Google Drive waiting/ senkron köprüsü — yalnızca ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminDriveController {

    private final DriveSyncService driveSyncService;

    public AdminDriveController(DriveSyncService driveSyncService) {
        this.driveSyncService = driveSyncService;
    }

    /** Drive {@code waiting/}'i lokale çek ve yeni dosyaları döndür. */
    @PostMapping(path = "/pull-waiting")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Drive waiting/ klasörünü lokale çek",
            description = "gdrive-ecommint remote'undan faturalar/waiting/ dizinini lokal waiting "
                    + "dizinine çeker (rclone copy; yalnızca remote→local PULL — lokal dosya ASLA "
                    + "Drive'a push edilmez) ve bu çağrıda inen yeni dosyaları döndürür. Manuel "
                    + "tetikleme. Köprü kapalıysa no-op (skipped=true). Yalnızca ADMIN.")
    public DriveSyncResult pullWaiting() {
        return driveSyncService.pullWaiting();
    }
}
