package com.ecommint.accounthr.controller;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.dto.dashboard.DashboardSummary;
import com.ecommint.accounthr.service.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E3-01 — Dashboard özet uçları (salt-okunur). {@code authenticated()} gerektirir.
 *
 * <p>Tek uç: {@code GET /api/v1/dashboard/summary?month=YYYY-MM}. {@code month}
 * verilmezse içinde bulunulan ay ({@link YearMonth#now()}) kullanılır. Bilinmeyen
 * ay sıfırlanmış özet döner (404/500 değil).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Aylık özet (KPI + durum dağılımı)")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Verilen ay için dashboard özetini döner. {@code month} null/boş ise içinde
     * bulunulan ay kodu kullanılır; o dönem yoksa yine sıfırlanmış özet döner.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING','TEAM_MEMBER')")
    @Operation(
            summary = "Aylık dashboard özeti",
            description = "Verilen ay (YYYY-MM) için KPI'lar ve fatura durumu dağılımını döner. "
                    + "month verilmezse içinde bulunulan ay kullanılır. Bilinmeyen ay → sıfır "
                    + "değerler (hata değil). Kimlik doğrulama gerektirir.")
    public DashboardSummary summary(@RequestParam(required = false) String month) {
        String resolved = resolveMonth(month);
        return dashboardService.getSummary(resolved);
    }

    /**
     * {@code month} parametresini normalize eder ve doğrular.
     *
     * <ul>
     *   <li>null/boş → içinde bulunulan ay ({@link YearMonth#now()}), {@code YYYY-MM}.</li>
     *   <li>iyi-biçimli {@code YYYY-MM} → normalize edilmiş {@code YYYY-MM} (echo bu
     *       değer olur; ham girdi DEĞİL). Bilinmeyen ama iyi-biçimli ay hata değildir;
     *       servis sıfır özet döner.</li>
     *   <li>biçimsiz/bozuk string → {@link InvalidMonthException} (400 INVALID_MONTH).</li>
     * </ul>
     */
    private String resolveMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now().format(MONTH_FORMAT);
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FORMAT).format(MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new InvalidMonthException("month must be in YYYY-MM format.", ex);
        }
    }
}
