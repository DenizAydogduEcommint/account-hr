package com.ecommint.accounthr.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

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
import com.ecommint.accounthr.dto.invoiceparse.ParsedInvoiceResponse;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.service.InvoiceUploadService;
import com.ecommint.accounthr.service.invoiceparse.InvoicePdfParser;
import com.ecommint.accounthr.service.invoiceparse.ParsedInvoice;
import com.ecommint.accounthr.service.storage.StorageException;

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
    private final InvoicePdfParser invoicePdfParser;

    public InvoiceController(InvoiceUploadService invoiceUploadService,
            AppUserRepository userRepository,
            InvoicePdfParser invoicePdfParser) {
        this.invoiceUploadService = invoiceUploadService;
        this.userRepository = userRepository;
        this.invoicePdfParser = invoicePdfParser;
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

    /**
     * E5-03 — Fatura PDF'ini çözümle (otomatik doldurma). Yüklenen PDF, depolanmadan
     * metni çıkarılıp yapılandırılmış alanlara çevrilir; istemci bu alanlarla fatura
     * formunu önceden doldurur. Hiçbir dosya kalıcılaştırılmaz.
     *
     * <p>Yetki: ADMIN/ACCOUNTING ({@code POST /api/v1/files} ile aynı ayrıcalık sınıfı —
     * fatura içeriğini okur ama persist etmez).
     *
     * <p>Doğrulama: boş dosya, 10MB üstü ya da {@code .pdf} olmayan uzantı → 400
     * (StorageException; {@code InvoiceUploadService} ile tutarlı). BOZUK ama .pdf bir
     * dosya → 200 + warnings (parser asla istisna fırlatmaz). KAPSAM: yalnızca PDF;
     * JPG/görüntü OCR gelecek iş.
     *
     * @param file zorunlu — fatura PDF'i (≤ 10MB)
     * @return çözümlenmiş alanlar + uyarılar (rawText hariç)
     */
    @PostMapping(path = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(
            summary = "Fatura PDF'ini çözümle (otomatik doldurma)",
            description = "Bir fatura PDF'i yüklenir; metni çıkarılıp fatura numarası, tarih, "
                    + "toplam, para birimi, KDV ve sağlayıcı alanları döner. Dosya DEPOLANMAZ. "
                    + "Çözümlenemeyen alanlar null + warnings. Yalnızca PDF; JPG/OCR kapsam dışı.")
    public ResponseEntity<ParsedInvoiceResponse> parse(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new StorageException("Boş dosya çözümlenemez.");
        }
        if (file.getSize() > InvoiceUploadService.MAX_FILE_SIZE_BYTES) {
            throw new StorageException("Dosya boyutu 10MB sınırını aşıyor: "
                    + file.getOriginalFilename());
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ENGLISH).endsWith(".pdf")) {
            throw new StorageException("Yalnızca PDF çözümlenebilir (izinli uzantı: .pdf): " + name);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new StorageException("Yüklenen dosya okunamadı.", e);
        }

        ParsedInvoice parsed = invoicePdfParser.parse(bytes);
        return ResponseEntity.ok(ParsedInvoiceResponse.from(parsed));
    }

    /** Authentication principal (e-posta) → AppUser. Bulunamazsa null (uploaded_by nullable). */
    private AppUser resolveUploader(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
