package com.ecommint.accounthr.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.ecommint.accounthr.service.storage.StorageException;
import com.ecommint.accounthr.service.storage.StorageService;
import com.ecommint.accounthr.service.storage.StoredFile;

/**
 * Fatura dosyası uçları (E1-04). Tümü authenticated (SecurityConfig
 * anyRequest().authenticated()). Yüklenen dosya storage köküne yazılır, metadata
 * {@code files} tablosuna işlenir.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final StorageService storageService;
    private final FileAssetRepository fileAssetRepository;
    private final InvoiceRepository invoiceRepository;
    private final AppUserRepository userRepository;

    public FileController(StorageService storageService,
                          FileAssetRepository fileAssetRepository,
                          InvoiceRepository invoiceRepository,
                          AppUserRepository userRepository) {
        this.storageService = storageService;
        this.fileAssetRepository = fileAssetRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
    }

    /** Dosya yükle + FileAsset kaydı oluştur. */
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
        Long providerId = invProvider != null ? invProvider.getId() : null;
        String effectiveInvoiceNo = invoiceNo != null ? invoiceNo : invoice.getInvoiceNo();

        StoredFile stored;
        try {
            stored = storageService.store(
                    invoiceId,
                    invoiceDate,
                    serviceName,
                    providerId,
                    effectiveInvoiceNo,
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    type);
        } catch (IOException e) {
            throw new StorageException("Yüklenen dosya okunamadı.", e);
        }

        AppUser uploader = resolveUploader(authentication);

        FileAsset asset = new FileAsset();
        asset.setInvoice(invoice);
        asset.setFilePath(stored.relativePath());
        asset.setFileName(stored.fileName());
        asset.setFileType(type);
        asset.setMimeType(file.getContentType());
        asset.setSizeBytes(stored.sizeBytes());
        asset.setSha256(stored.sha256());
        asset.setUploadedBy(uploader);
        asset = fileAssetRepository.save(asset);

        return ResponseEntity.status(HttpStatus.CREATED).body(FileResponse.from(asset));
    }

    /** Bir invoice'a bağlı dosyaların metadata listesi. */
    @GetMapping
    public List<FileResponse> list(@RequestParam("invoiceId") Long invoiceId) {
        return fileAssetRepository.findByInvoiceId(invoiceId).stream()
                .map(FileResponse::from)
                .toList();
    }

    /** Dosya indir (akış). */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id) {
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new StorageException("Dosya kaydı bulunamadı: id=" + id));
        Resource resource = storageService.loadAsResource(id);

        String contentType = asset.getMimeType() != null
                ? asset.getMimeType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asset.getFileName() + "\"")
                .body(resource);
    }

    /** Dosyayı trash/ klasörüne taşı (silmez). */
    @PostMapping("/{id}/trash")
    public ResponseEntity<FileResponse> trash(@PathVariable("id") Long id) {
        storageService.moveToTrash(id);
        FileAsset asset = fileAssetRepository.findById(id)
                .orElseThrow(() -> new StorageException("Dosya kaydı bulunamadı: id=" + id));
        return ResponseEntity.ok(FileResponse.from(asset));
    }

    /** Dosyayı waiting/ klasörüne taşı. */
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
