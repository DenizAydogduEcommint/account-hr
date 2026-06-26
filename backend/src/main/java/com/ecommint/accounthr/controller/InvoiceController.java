package com.ecommint.accounthr.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.dto.invoice.InvoiceUploadResponse;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.service.InvoiceUploadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E3-05 — Fatura yükleme ucu. Ekip üyesi servis + ay seçer, tutar/para birimi/açıklama
 * girer, bir veya birden çok dosya (PDF/XML/JPG/PNG) yükler; ilgili harcamanın fatura
 * durumu otomatik {@code Bulundu} (ya da e-Fatura işaretliyse {@code e-Fatura}) olur ve
 * dosyalar STORAGE_ROOT altına kaydedilir.
 *
 * <p>{@code authenticated()} gerektirir (SecurityConfig {@code anyRequest().authenticated()};
 * ek olarak {@code @PreAuthorize("isAuthenticated()")} ile açıkça belirtilir). İş mantığı +
 * atomiklik {@link InvoiceUploadService}'de; bu controller ince kalır.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Fatura yükleme — servis + ay seç, dosya(lar) yükle")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

    private final InvoiceUploadService invoiceUploadService;
    private final AppUserRepository userRepository;

    public InvoiceController(InvoiceUploadService invoiceUploadService,
            AppUserRepository userRepository) {
        this.invoiceUploadService = invoiceUploadService;
        this.userRepository = userRepository;
    }

    /**
     * Bir fatura yükler: dosya(lar) + metadata + durum güncellemesi tek atomik işlemde.
     *
     * @param serviceId   zorunlu — servis id (yoksa 404)
     * @param month       zorunlu — {@code YYYY-MM} (biçimsiz → 400)
     * @param amount      opsiyonel — orijinal tutar
     * @param currency    opsiyonel — para birimi (TRY/USD/EUR/GBP; null → TRY)
     * @param description opsiyonel — fatura notuna eklenecek açıklama
     * @param eInvoice    opsiyonel — true ise durum e-Fatura, aksi halde Bulundu
     * @param kdvRate     opsiyonel — KDV oranı yüzde (E3-11, 0–100; ör. 20.00). Verilirse
     *                    faturanın brüt TL'sinden matrah + KDV türetilir; null → KDV alanları
     *                    null. Aralık dışı → 400.
     * @param files       1..N dosya (PDF/XML/JPG/PNG, her biri ≤ 10MB)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING','TEAM_MEMBER')")
    @Operation(
            summary = "Fatura yükle (servis + ay + dosyalar)",
            description = "Multipart form-data ile servis + ay seçilip dosya(lar) yüklenir. "
                    + "Servis + dönem için var olan harcama (ör. Bekleniyor satırı) kullanılır, "
                    + "yoksa yenisi oluşturulur. Fatura durumu Bulundu (eInvoice=true ise e-Fatura) "
                    + "olur; böylece eksik fatura sayacı azalır. Dosyalar STORAGE_ROOT altına "
                    + "kaydedilir. Kimlik doğrulama gerektirir.")
    public ResponseEntity<InvoiceUploadResponse> upload(
            @RequestParam("serviceId") Long serviceId,
            @RequestParam("month") String month,
            @RequestParam(value = "amount", required = false) BigDecimal amount,
            @RequestParam(value = "currency", required = false) Currency currency,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "eInvoice", required = false, defaultValue = "false") boolean eInvoice,
            @RequestParam(value = "kdvRate", required = false) BigDecimal kdvRate,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {

        AppUser uploader = resolveUploader(authentication);

        InvoiceUploadResponse response = invoiceUploadService.upload(
                serviceId, month, amount, currency, description, eInvoice, kdvRate, files, uploader);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Authentication principal (e-posta) → AppUser. Bulunamazsa null (uploaded_by nullable). */
    private AppUser resolveUploader(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
