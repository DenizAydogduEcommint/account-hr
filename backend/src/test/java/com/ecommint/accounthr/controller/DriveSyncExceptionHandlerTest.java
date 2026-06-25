package com.ecommint.accounthr.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.service.drive.DriveSyncException;
import com.ecommint.accounthr.service.drive.DriveSyncValidationException;

/**
 * {@link GlobalExceptionHandler} Drive eşlemeleri: ÇAĞIRAN GİRDİSİ hatası
 * ({@link DriveSyncValidationException}, ör. traversal dosya adı) HTTP <b>400</b>
 * DRIVE_REQUEST_INVALID döner; DIŞ bağımlılık hatası (düz {@link DriveSyncException},
 * ör. rclone başarısızlığı) HTTP <b>502</b> DRIVE_SYNC_ERROR döner.
 *
 * <p>Delete ucu controller'da AÇIK DEĞİL (internal-only); bu yüzden burada handler'ı
 * uçtan uca doğrulamak için test-içi minik bir controller kullanılır. GERÇEK rclone
 * exec EDİLMEZ — başarısızlık {@link DriveSyncException} fırlatılarak simüle edilir.
 * Standalone MockMvc (Spring context / DB gerektirmez).
 */
class DriveSyncExceptionHandlerTest {

    /** Test-içi controller: girdiye göre validation (400) veya dış-bağımlılık (502) hatası fırlatır. */
    @RestController
    static final class TestDriveController {
        @PostMapping("/test/drive/delete")
        public void delete(@RequestParam String fileName) {
            if (fileName.contains("/") || fileName.contains("..") || fileName.contains(":")) {
                throw new DriveSyncValidationException(
                        "Geçersiz dosya adı (yol ayırıcı/traversal): " + fileName);
            }
            // Geçerli ad ama "rclone başarısız" → dış bağımlılık hatası (502 beklenir).
            throw new DriveSyncException("Drive waiting'den silme başarısız (rclone exit=5).");
        }
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new TestDriveController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void traversalFileName_returns400_not502() throws Exception {
        mockMvc().perform(post("/test/drive/delete").param("fileName", "../escape.pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("DRIVE_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/test/drive/delete"));
    }

    @Test
    void simulatedRcloneFailure_returns502() throws Exception {
        mockMvc().perform(post("/test/drive/delete").param("fileName", "aws_subat.pdf"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("DRIVE_SYNC_ERROR"));
    }
}
