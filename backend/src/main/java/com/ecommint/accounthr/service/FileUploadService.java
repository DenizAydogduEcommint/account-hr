package com.ecommint.accounthr.service;

import java.io.InputStream;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.service.storage.StorageService;
import com.ecommint.accounthr.service.storage.StoredFile;

/**
 * Fatura dosyası yükleme iş mantığı (E1-04 fix): fiziksel dosyayı depola + {@link FileAsset}
 * metadata'sını kalıcılaştır işlemini TEK transaction'da birleştirir.
 *
 * <p>Sorun: önce fiziksel dosya diske yazılır, sonra DB'ye FileAsset kaydedilir. DB save
 * herhangi bir sebeple fırlatırsa (constraint, bağlantı kopması vb.) fiziksel dosya diskte
 * ORPHAN kalır. Bu servis metadata persist'i başarısız olduğunda az önce yazılan fiziksel
 * dosyayı en-iyi-çaba (best-effort) siler ve hatayı yeniden fırlatır; böylece "kayıtsız
 * dosya" sızıntısı önlenir. Controller ince kalır, API sözleşmesi/dosya adları değişmez.
 */
@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final StorageService storageService;
    private final FileAssetRepository fileAssetRepository;

    public FileUploadService(StorageService storageService,
                             FileAssetRepository fileAssetRepository) {
        this.storageService = storageService;
        this.fileAssetRepository = fileAssetRepository;
    }

    /**
     * Fiziksel dosyayı depolar ve {@link FileAsset} kaydını oluşturur. Metadata persist'i
     * başarısız olursa az önce yazılan fiziksel dosya silinir (orphan bırakılmaz) ve hata
     * yeniden fırlatılır.
     */
    @Transactional
    public FileAsset upload(Invoice invoice,
                            LocalDate invoiceDate,
                            String serviceName,
                            Long providerId,
                            String effectiveInvoiceNo,
                            String originalFilename,
                            InputStream content,
                            String mimeType,
                            FileType type,
                            AppUser uploader) {

        StoredFile stored = storageService.store(
                invoice.getId(),
                invoiceDate,
                serviceName,
                providerId,
                effectiveInvoiceNo,
                originalFilename,
                content,
                type);

        try {
            FileAsset asset = new FileAsset();
            asset.setInvoice(invoice);
            asset.setFilePath(stored.relativePath());
            asset.setFileName(stored.fileName());
            asset.setFileType(type);
            asset.setMimeType(mimeType);
            asset.setSizeBytes(stored.sizeBytes());
            asset.setSha256(stored.sha256());
            asset.setUploadedBy(uploader);
            return fileAssetRepository.save(asset);
        } catch (RuntimeException e) {
            // Metadata persist başarısız → orphan fiziksel dosyayı temizle (best-effort).
            log.warn("FileAsset persist başarısız; orphan dosya temizleniyor: {}",
                    stored.relativePath(), e);
            storageService.deletePhysical(stored.relativePath());
            throw e;
        }
    }

    /** Provider'dan provider id (null güvenli). */
    public static Long providerIdOf(Provider provider) {
        return provider != null ? provider.getId() : null;
    }
}
