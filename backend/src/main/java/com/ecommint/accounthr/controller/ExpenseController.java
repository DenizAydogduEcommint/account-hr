package com.ecommint.accounthr.controller;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.expense.ExpenseCreateRequest;
import com.ecommint.accounthr.dto.expense.ExpenseListResponse;
import com.ecommint.accounthr.dto.expense.ExpenseRow;
import com.ecommint.accounthr.dto.expense.StatusUpdateRequest;
import com.ecommint.accounthr.service.ExpenseCommandService;
import com.ecommint.accounthr.service.ExpenseQueryService;

import jakarta.validation.Valid;

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
    private final ExpenseCommandService expenseCommandService;

    public ExpenseController(ExpenseQueryService expenseQueryService,
            ExpenseCommandService expenseCommandService) {
        this.expenseQueryService = expenseQueryService;
        this.expenseCommandService = expenseCommandService;
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
     * E3-06 — Elle (manuel) bir harcama satırı oluşturur. Ekip üyeleri girebilir
     * ({@code isAuthenticated()}, E3-05 fatura yükleme ile aynı yetki). Satır
     * {@code source=MANUAL} ile işaretlenir ve durumu "Bekleniyor" (EXPECTED) olan bir
     * taslak invoice ile gelir. Yanıt, GET listesindeki satırla BİREBİR aynı
     * {@link ExpenseRow}'dur (201 Created).
     *
     * <p>Doğrulama: alan-bazı hatalar (null/format/pozitiflik) → 400 VALIDATION_ERROR;
     * bilinmeyen {@code serviceId} → 404 NOT_FOUND; bilinmeyen {@code cardLast4}/
     * {@code usingTeamId} → 400 VALIDATION_ERROR (hepsi {@code GlobalExceptionHandler}).
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Elle harcama satırı oluştur (E3-06)",
            description = "Banka ekstresinden önce/olmadan tek bir harcama satırı girer "
                    + "(servis-tabanlı). source=MANUAL, taslak invoice durumu EXPECTED "
                    + "(Bekleniyor). Yanıt GET listesindeki satırla aynı ExpenseRow. "
                    + "Kimlik doğrulama gerektirir.")
    public ExpenseRow create(@Valid @RequestBody ExpenseCreateRequest request) {
        return expenseCommandService.create(request);
    }

    /**
     * E3-07 — Bir harcama satırının fatura durumunu elle değiştirir
     * ({@code isAuthenticated()}). Durum, satırın TEMSİLCİ invoice'unda (en güncel =
     * en yüksek id'li; GET listesiyle aynı tanım) güncellenir. Geçiş bir state machine'den
     * ({@code InvoiceStatusPolicy}) geçer — MVP'de tüm geçişler serbesttir (permissive +
     * audit); ileride kapatılan bir geçiş 409 {@code ILLEGAL_STATUS_TRANSITION} döner.
     *
     * <p>Yanıt, GET listesindekiyle BİREBİR aynı {@link ExpenseRow}'dur; {@code invoiceStatus}
     * ve türetilmiş {@code invoiceColorHex} yeni durumu yansıtır (renk/metin tek kaynak
     * {@code StatusColors}/{@code StatusText}'ten gelir, istekten ALINMAZ). Durum değişimi
     * {@code audit_log}'a {@code STATUS_CHANGE} (kim + ne zaman + eski→yeni) olarak düşer.
     *
     * <p>Doğrulama: bilinmeyen {@code id} → 404 NOT_FOUND; eksik/geçersiz {@code status}
     * gövdesi → 400 ({@code GlobalExceptionHandler}). IGNORED'a geçiş operasyonel toplamı
     * DEĞİŞTİRMEZ ({@code informational} bayrağı ayrıdır, dokunulmaz).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Fatura durumunu elle değiştir (E3-07)",
            description = "Satırın temsilci (max-id) invoice'unun durumunu günceller. Geçiş "
                    + "state machine'den geçer (MVP: permissive + audit). Yanıt GET listesindeki "
                    + "ExpenseRow; invoiceColorHex durumdan türetilir (tek kaynak). Değişim "
                    + "audit_log'a STATUS_CHANGE olarak yazılır. Kimlik doğrulama gerektirir.")
    public ExpenseRow updateStatus(@PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return expenseCommandService.updateStatus(id, request);
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
