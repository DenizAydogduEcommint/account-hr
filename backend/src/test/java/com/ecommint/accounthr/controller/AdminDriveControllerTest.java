package com.ecommint.accounthr.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ecommint.accounthr.dto.drive.DriveSyncResult;
import com.ecommint.accounthr.service.drive.DriveSyncException;
import com.ecommint.accounthr.service.drive.DriveSyncService;

/**
 * E2-06 — {@link AdminDriveController} + {@link GlobalExceptionHandler} HTTP davranışı.
 *
 * <p>Standalone MockMvc (Spring context / DB / gerçek rclone GEREKTİRMEZ): servis
 * mock'lanır. Drive erişilemez/rclone yok durumunda endpoint TEMİZ bir 502 döndürmeli
 * (genel handler'a düşüp 500 OLMAMALI); köprü kapalıyken 200 + skipped=true.
 */
class AdminDriveControllerTest {

    private MockMvc mockMvc(DriveSyncService svc) {
        return MockMvcBuilders.standaloneSetup(new AdminDriveController(svc))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void driveUnavailable_returns502DriveSyncError_not500() throws Exception {
        DriveSyncService svc = Mockito.mock(DriveSyncService.class);
        when(svc.pullWaiting()).thenThrow(
                new DriveSyncException("rclone çalıştırılamadı (kurulu değil veya erişilemez)."));

        mockMvc(svc).perform(post("/api/v1/admin/drive/pull-waiting"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("DRIVE_SYNC_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/admin/drive/pull-waiting"));
    }

    @Test
    void disabledBridge_returns200WithSkippedResult() throws Exception {
        DriveSyncService svc = Mockito.mock(DriveSyncService.class);
        when(svc.pullWaiting()).thenReturn(
                DriveSyncResult.skipped("Drive sync köprüsü kapalı (app.drive.enabled=false)."));

        mockMvc(svc).perform(post("/api/v1/admin/drive/pull-waiting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(true))
                .andExpect(jsonPath("$.pulledCount").value(0));
    }

    @Test
    void successfulPull_returnsNewFiles() throws Exception {
        DriveSyncService svc = Mockito.mock(DriveSyncService.class);
        when(svc.pullWaiting()).thenReturn(
                new DriveSyncResult(1, List.of("aws_subat.pdf"), false, "1 yeni dosya çekildi."));

        mockMvc(svc).perform(post("/api/v1/admin/drive/pull-waiting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped").value(false))
                .andExpect(jsonPath("$.pulledCount").value(1))
                .andExpect(jsonPath("$.newFiles[0]").value("aws_subat.pdf"));
    }
}
