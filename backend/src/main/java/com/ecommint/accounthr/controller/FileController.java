package com.ecommint.accounthr.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.dto.file.FileResponse;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.service.FileUploadService;
import com.ecommint.accounthr.service.ResourceNotFoundException;
import com.ecommint.accounthr.service.storage.StorageException;
import com.ecommint.accounthr.service.storage.StorageService;

/**
 * Fatura dosyası uçları (E1-04). Tümü authenticated (SecurityConfig
 * anyRequest().authenticated()). Yüklenen dosya storage köküne yazılır, metadata
 * {@code files} tablosuna işlenir.
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final StorageService storageService;
    private final FileUploadService fileUploadService;
    private final FileAssetRepository fileAssetRepository;
    private final InvoiceRepository invoiceRepository;
    private final AppUserRepository userRepository;

    public FileController(StorageService storageService,
                          FileUploadService fileUploadService,
                          FileAssetRepository fileAssetRepository,
                          InvoiceRepository invoiceRepository,
                          AppUserRepository userRepository) {
        this.storageService = storageService;
        this.fileUploadService = fileUploadService;
        this.fileAssetRepository = fileAssetRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Dosya yükle + FileAsset kaydı oluştur.
     *
     * <p>Bu generic/admin dosya ucu fatura PDF'lerini doğrudan storage köküne yazdığından
     * en ayrıcalıklı işlemdir; kardeş okuma/taşıma uçlarıyla tutarlı olarak yalnızca
     * ADMIN/ACCOUNTING rollerine açıktır. (E3-05 self-service yükleme yolu
     * {@code POST /api/v1/invoices} ayrıdır ve TEAM_MEMBER'a açık kalır.)
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("invoiceId") Long invoiceId,
            @RequestParam("invoiceDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
            @RequestParam("serviceName") String serviceName,
            @RequestParam(value = "fileType", required = false) FileType fileType,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "invoiceNo", required = false) String invoiceNo,
            Authentication authentication) {

        if (file == null || file.isEmpty()) {
            throw new StorageException("Boş dosya yüklenemez.");
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new StorageException("Invoice bulunamadı: id=" + invoiceId));

        FileType type = fileType != null ? fileType : FileType.OTHER;

        // Duplicate kontrolü için provider/invoiceNo: parametre verilmemişse invoice'tan türet.
        Provider invProvider = invoice.getProvider();
        Long providerId = FileUploadService.providerIdOf(invProvider);
        String effectiveInvoiceNo = invoiceNo != null ? invoiceNo : invoice.getInvoiceNo();

        AppUser uploader = resolveUploader(authentication);

        InputStream content;
        try {
            content = file.getInputStream();
        } catch (IOException e) {
            throw new StorageException("Yüklenen dosya okunamadı.", e);
        }

        // Depola + metadata persist'i tek transaction'da yapılır; persist başarısız
        // olursa orphan fiziksel dosya FileUploadService tarafından silinir.
        FileAsset asset = fileUploadService.upload(
                invoice,
                invoiceDate,
                serviceName,
                providerId,
                effectiveInvoiceNo,
                file.getOriginalFilename(),
                content,
                file.getContentType(),
                type,
                uploader);

        return ResponseEntity.status(HttpStatus.CREATED).body(FileResponse.from(asset));
    }

    /**
     * Bir invoice'a bağlı dosyaların metadata listesi.
     *
     * <p>E3-08: okuma uçları (list/download) ADMIN/ACCOUNTING/TEAM_MEMBER üç role de açıktır;
     * ekip üyeleri yükledikleri fatura dosyalarını görebilmeli. Yazma/taşıma uçları (generic
     * POST /files, trash, waiting) yalnızca ADMIN/ACCOUNTING'de kalır.
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING','TEAM_MEMBER')")
    @GetMapping
    public List<FileResponse> list(@RequestParam("invoiceId") Long invoiceId) {
        return fileAssetRepository.findByInvoiceId(invoiceId).stream()
                .map(FileResponse::from)
                .toList();
    }

    /**
     * Dosya indir (akış).
     *
     * <p>E3-08: indirme ADMIN/ACCOUNTING/TEAM_MEMBER üç role de açıktır (ekip üyeleri
     * yükledikleri dosyalara erişebilmeli). Taşıma/yazma uçları ADMIN/ACCOUNTING'de kalır.
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING','TEAM_MEMBER')")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosya kaydı bulunamadı: id=" + id));

        // Fiziksel dosya eksik/okunamaz ise loadAsResource StorageException (→400) fırlatır;
        // indirme sözleşmesi de (önizleme gibi) bunu 404'e çevirir: kayıt var ama dosya yok →
        // istemci hatası (400) değil, kaynak yok (404). Zaten yüklenmiş asset geçilerek ikinci
        // DB turu önlenir.
        Resource resource;
        try {
            resource = storageService.loadAsResource(asset);
        } catch (StorageException e) {
            throw new ResourceNotFoundException("Dosya bulunamadı: id=" + id);
        }

        String contentType = asset.getMimeType() != null
                ? asset.getMimeType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // RFC 5987 kodlama: migrasyon yolu (copyPreservingPath) kaynak dosya adlarını
        // aynen korur (slugify edilmez); bu nedenle dosya adı CR/LF/" içerebilir. Header
        // injection'ı önlemek için adı ContentDisposition ile güvenli biçimde kodla.
        String contentDisposition = ContentDisposition.attachment()
                .filename(asset.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    /**
     * Dosya önizle (akış, satır-içi) — E3-09.
     *
     * <p>{@code download} ile AYNI güvenli yol çözümü + akışı kullanır; tek farkı
     * {@code Content-Disposition: inline}'dır: tarayıcı dosyayı İNDİRMEK yerine iframe/img
     * içinde RENDER eder. {@code Content-Type} depolanan {@code mimeType}'tan gelir
     * (application/pdf, image/jpeg, image/png). XML için {@code text/xml; charset=utf-8}
     * kullanılır (tarayıcı ham metni gösterir). {@code mimeType} yoksa tipe göre makul bir
     * varsayılana düşülür.
     *
     * <p>Yetki: ADMIN/ACCOUNTING/TEAM_MEMBER (download ile aynı). FileAsset yoksa VEYA fiziksel
     * dosya eksik/okunamaz ise → 404 NOT_FOUND (500 DEĞİL). Inline dosya adı RFC 5987 ile güvenli
     * kodlanır (header injection'a karşı; {@code download} ile aynı).
     *
     * <p><b>NOT (MVP):</b> Yanıt basit bir {@link Resource} akışıdır; HTTP Range (kısmi içerik /
     * byte-range) istekleri DESTEKLENMEZ — büyük PDF'lerde tarayıcı tamamını çeker. İleride
     * {@code ResourceRegionHttpMessageConverter} ile range desteği eklenebilir.
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING','TEAM_MEMBER')")
    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable("id") Long id) {
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dosya kaydı bulunamadı: id=" + id));

        // Fiziksel dosya eksik/okunamaz ise loadAsResource StorageException (→400) fırlatır;
        // önizleme sözleşmesi bunu 404'e çevirir (kaynak yok, istemci hatası değil sunucu 500
        // de değil). Asıl mesaj (mutlak yol) sızdırılmaz; sabit 404 mesajı döner.
        Resource resource;
        try {
            resource = storageService.loadAsResource(asset);
        } catch (StorageException e) {
            throw new ResourceNotFoundException("Önizlenecek dosya bulunamadı: id=" + id);
        }

        MediaType contentType = resolvePreviewContentType(asset);

        // Inline: tarayıcı render etsin (indirme değil). Dosya adı RFC 5987 ile güvenli kodlanır.
        String contentDisposition = ContentDisposition.inline()
                .filename(asset.getFileName(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    /**
     * Önizleme {@code Content-Type}'ını belirler. PDF/görüntü için depolanan {@code mimeType}
     * aynen kullanılır. XML/metin için tarayıcının inline gösterebilmesi adına
     * {@code text/xml; charset=utf-8}'e normalize edilir. {@code mimeType} yoksa dosya tipinden
     * makul bir varsayılan türetilir; hiçbiri uymazsa {@code application/octet-stream}.
     */
    private MediaType resolvePreviewContentType(FileAsset asset) {
        String mime = asset.getMimeType();
        if (mime != null && !mime.isBlank()) {
            String m = mime.toLowerCase(java.util.Locale.ROOT);
            // XML/metin → charset'li text/xml ki tarayıcı inline göstersin.
            if (m.contains("xml")) {
                return MediaType.parseMediaType("text/xml; charset=utf-8");
            }
            return MediaType.parseMediaType(mime);
        }
        // mimeType yok → tipe göre varsayılan.
        return switch (asset.getFileType()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case XML -> MediaType.parseMediaType("text/xml; charset=utf-8");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Dosyayı trash/ klasörüne taşı (silmez).
     *
     * <p>Geçici yetki kapısı: yalnızca ADMIN/ACCOUNTING. Tam per-invoice (nesne-düzeyi
     * sahiplik) yetkilendirmesi E3-08'e ertelenmiştir.
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @PostMapping("/{id}/trash")
    public ResponseEntity<FileResponse> trash(@PathVariable("id") Long id) {
        storageService.moveToTrash(id);
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new StorageException("Dosya kaydı bulunamadı: id=" + id));
        return ResponseEntity.ok(FileResponse.from(asset));
    }

    /**
     * Dosyayı waiting/ klasörüne taşı.
     *
     * <p>Geçici yetki kapısı: yalnızca ADMIN/ACCOUNTING. Tam per-invoice (nesne-düzeyi
     * sahiplik) yetkilendirmesi E3-08'e ertelenmiştir.
     */
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @PostMapping("/{id}/waiting")
    public ResponseEntity<FileResponse> waiting(@PathVariable("id") Long id) {
        storageService.moveToWaiting(id);
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new StorageException("Dosya kaydı bulunamadı: id=" + id));
        return ResponseEntity.ok(FileResponse.from(asset));
    }

    /** Authentication principal (e-posta) → AppUser. Bulunamazsa null (uploaded_by nullable). */
    private AppUser resolveUploader(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
