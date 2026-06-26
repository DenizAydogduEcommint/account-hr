package com.ecommint.accounthr.controller;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.dto.missing.MissingInvoiceListResponse;
import com.ecommint.accounthr.service.MissingInvoiceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E3-04 — Eksik fatura ekranı ucu (servis ↔ ay çapraz doğrulama, salt-okunur).
 * {@code authenticated()} gerektirir.
 *
 * <p>Tek uç: {@code GET /api/v1/missing-invoices?month=YYYY-MM}. Seçili ayda kontrol
 * kümesindeki (Aktif=Evet, bilgi-amaçlı değil; Aylık her ay ya da Yıllık ise yalnızca
 * Aktif Aylar'ında bu ay olan) servislerden faturası bulunmamış olanları döner.
 *
 * <p>{@code month} doğrulaması dashboard ile aynı ({@link YearMonth#parse}): boş → içinde
 * bulunulan ay; iyi-biçimli ama bilinmeyen ay → boş liste (hata değil); biçimsiz → 400
 * {@code INVALID_MONTH}.
 */
@RestController
@RequestMapping("/api/v1/missing-invoices")
@Tag(name = "Missing Invoices", description = "Eksik fatura — servis ↔ ay çapraz doğrulama")
@SecurityRequirement(name = "bearerAuth")
public class MissingInvoiceController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MissingInvoiceService missingInvoiceService;

    public MissingInvoiceController(MissingInvoiceService missingInvoiceService) {
        this.missingInvoiceService = missingInvoiceService;
    }

    /**
     * Verilen ay için faturası eksik servisleri listeler. {@code month} verilmezse içinde
     * bulunulan ay; iyi-biçimli ama bilinmeyen ay boş liste döner; biçimsiz → 400.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING','TEAM_MEMBER')")
    @Operation(
            summary = "Bir ayda faturası eksik servisler",
            description = "Servis ↔ ay çapraz doğrulama: Aktif=Evet, bilgi-amaçlı olmayan "
                    + "Aylık (her ay) ve Yıllık (yalnızca Aktif Aylar'ında bu ay olan) "
                    + "servislerden, o dönemde durumu Bulundu/e-Fatura olan harcaması "
                    + "OLMAYANLARI döner (Bekleniyor = henüz fatura yok = eksik). Kullanım "
                    + "bazlı/Ad-hoc servisler dahil değildir. month verilmezse içinde bulunulan "
                    + "ay; bilinmeyen ay → boş liste; biçimsiz → 400 INVALID_MONTH. Eksik sayısı "
                    + "dashboard sayacıyla birebir aynıdır. Kimlik doğrulama gerektirir. Yanıt zarf "
                    + "biçimindedir: {items, count, approxTotalTry} (E3-10).")
    public MissingInvoiceListResponse list(@RequestParam(required = false) String month) {
        String resolved = resolveMonth(month);
        return missingInvoiceService.findMissingResponse(resolved);
    }

    /**
     * {@code month} parametresini normalize eder ve doğrular (dashboard ile aynı kural):
     * null/boş → içinde bulunulan ay; iyi-biçimli → normalize {@code YYYY-MM}; biçimsiz →
     * {@link InvalidMonthException} (400 INVALID_MONTH). İyi-biçimli ama bilinmeyen ay hata
     * DEĞİLDİR (servis boş liste döner).
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
