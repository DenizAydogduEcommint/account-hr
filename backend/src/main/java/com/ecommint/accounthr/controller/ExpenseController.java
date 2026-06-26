package com.ecommint.accounthr.controller;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.expense.ExpenseListResponse;
import com.ecommint.accounthr.service.ExpenseQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E3-03 — Aylık harcamalar ekranı (12 kolonlu tablo) listeleme ucu (salt-okunur).
 * {@code authenticated()} gerektirir.
 *
 * <p>Tek uç: {@code GET /api/v1/expenses?month=YYYY-MM&card=&status=&q=&page=&size=}.
 * ANA (operasyonel, {@code informational=false}) harcama satırları sayfalı döner;
 * bilgi-amaçlı satırlar ({@code informational=true}: Multinet/sigorta/vergi) AYRI listede
 * ve operasyonel toplama dahil EDİLMEDEN gelir.
 *
 * <p>{@code month} doğrulaması dashboard ile aynı ({@link YearMonth#parse}): boş → içinde
 * bulunulan ay; iyi-biçimli ama bilinmeyen ay → boş sayfa + sıfır toplamlar (hata değil);
 * biçimsiz → 400 {@code INVALID_MONTH}. {@code status} geçersiz enum ise Spring
 * {@code MethodArgumentTypeMismatchException} → 400 {@code VALIDATION_ERROR}.
 */
@RestController
@RequestMapping("/api/v1/expenses")
@Tag(name = "Expenses", description = "Aylık harcamalar (12 kolonlu tablo) — filtre + arama + sayfalama")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ExpenseQueryService expenseQueryService;

    public ExpenseController(ExpenseQueryService expenseQueryService) {
        this.expenseQueryService = expenseQueryService;
    }

    /**
     * Bir ayın harcamalarını listeler. Opsiyonel filtreler (yalnızca ANA satırlara):
     * {@code ?card=} (son-4), {@code ?status=FOUND|E_INVOICE|EXPECTED|TO_INVESTIGATE|IGNORED},
     * {@code ?q=} (hizmet/sağlayıcı). Sayfalama: {@code ?page=&size=&sort=}.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Aylık harcamalar listesi (12 kolon, sayfalı)",
            description = "month verilmezse içinde bulunulan ay. Filtreler yalnızca ANA "
                    + "(informational=false) satırlara uygulanır: ?card=son4, "
                    + "?status=FOUND|E_INVOICE|EXPECTED|TO_INVESTIGATE|IGNORED, ?q=hizmet/sağlayıcı. "
                    + "Bilgi-amaçlı satırlar (Multinet/sigorta/vergi) ayrı listede, operasyonel "
                    + "toplama dahil değil. Bilinmeyen ay → boş + sıfır. Kimlik doğrulama gerektirir.")
    public ExpenseListResponse list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String card,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50, sort = "transactionDate", direction = Sort.Direction.ASC)
                    Pageable pageable) {
        String resolved = resolveMonth(month);
        return expenseQueryService.list(resolved, card, status, q, pageable);
    }

    /**
     * {@code month} parametresini normalize eder ve doğrular (dashboard ile aynı kural):
     * null/boş → içinde bulunulan ay; iyi-biçimli → normalize {@code YYYY-MM}; biçimsiz →
     * {@link InvalidMonthException} (400 INVALID_MONTH). İyi-biçimli ama bilinmeyen ay hata
     * DEĞİLDİR (servis boş sayfa + sıfır döner).
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
