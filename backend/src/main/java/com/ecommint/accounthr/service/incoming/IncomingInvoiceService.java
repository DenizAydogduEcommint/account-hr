package com.ecommint.accounthr.service.incoming;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.IncomingInvoice;
import com.ecommint.accounthr.domain.enums.IncomingSource;
import com.ecommint.accounthr.domain.enums.IncomingStatus;
import com.ecommint.accounthr.dto.incoming.IncomingInvoiceResponse;
import com.ecommint.accounthr.repository.IncomingInvoiceRepository;
import com.ecommint.accounthr.service.ResourceNotFoundException;
import com.ecommint.accounthr.service.storage.StorageService;
import com.ecommint.accounthr.service.storage.StoredFile;

/**
 * Ham/gelen fatura (incoming invoice) okuma + basit durum güncelleme servisi (E5-02).
 * Pull/tarama orkestrasyon {@link DriveWaitingPullService}'dedir; tek dosyanın KALICI
 * persist'i ({@link #ingestOne}) burada yapılır çünkü her dosyanın kendi tx'i (REQUIRES_NEW)
 * olmalı: eşzamanlı çift pull'da bir dosyanın unique-index çakışması yalnızca o dosyayı
 * atlamalı, batch'i geri almamalı (Spring self-invocation REQUIRES_NEW'i onurlandırmadığı
 * için ayrı bean'dedir). Bu servis ayrıca listeleme ve "ignore" sağlar.
 */
@Service
public class IncomingInvoiceService {

    private final IncomingInvoiceRepository repository;
    private final StorageService storageService;

    public IncomingInvoiceService(IncomingInvoiceRepository repository,
                                  StorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    /**
     * Tek bir landing dosyasını KENDİ tx'inde (REQUIRES_NEW) kalıcı store'a yazar ve bir
     * {@link IncomingInvoice} (NEW) oluşturur. Idempotency check-then-act yarışına karşı
     * dayanıklıdır: (a) önden (source, sourceRef)/sha256 kontrolü ile mükerrer atlanır;
     * (b) yarışı kaybeden eşzamanlı çağrı save'de {@link DataIntegrityViolationException}
     * (unique ux_incoming_source_ref) alırsa bu da atlama olarak ele alınır. Her iki
     * durumda da yalnızca BU dosya atlanır; çağıran döngü diğer dosyalara devam eder.
     *
     * <p>REQUIRES_NEW kritik: çağıranın (varsa) tx'i bu çakışmadan rollback-only
     * işaretlenmez; çakışma yalnızca bu iç tx'i geri alır.
     *
     * @return oluşturulan satır; mükerrer/çakışma nedeniyle atlandıysa {@link Optional#empty()}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IncomingInvoiceResponse> ingestOne(Path file, String sourceRef,
                                                       String fileName, String sha256) {
        // Idempotency (hızlı yol): aynı (kaynak, kaynak referansı) VEYA aynı içerik (sha256) varsa atla.
        if (repository.existsBySourceAndSourceRef(IncomingSource.DRIVE_WAITING, sourceRef)
                || repository.existsBySha256(sha256)) {
            return Optional.empty();
        }

        // Pull edilen kopyayı STORAGE_ROOT altında kalıcı bir yola yaz (landing geçici alandır).
        // Hedef yol: incoming/<sha256>/<fileName> — sha256 ile çakışmasız + idempotent.
        String relTarget = "incoming/" + sha256 + "/" + fileName;
        StoredFile stored;
        try (InputStream in = Files.newInputStream(file)) {
            stored = storageService.copyPreservingPath(relTarget, in);
        } catch (IOException e) {
            throw new RcloneException("Landing dosyası okunamadı: " + file, e);
        }

        IncomingInvoice entity = new IncomingInvoice();
        entity.setSource(IncomingSource.DRIVE_WAITING);
        entity.setSourceRef(sourceRef);
        entity.setFileName(fileName);
        entity.setStoredPath(stored.relativePath());
        entity.setSha256(stored.sha256());
        entity.setReceivedAt(java.time.Instant.now());
        entity.setStatus(IncomingStatus.NEW);

        try {
            IncomingInvoice saved = repository.saveAndFlush(entity);
            return Optional.of(IncomingInvoiceResponse.from(saved));
        } catch (DataIntegrityViolationException dup) {
            // Eşzamanlı pull yarışı: check'i geçtik ama başka bir çağrı önce save etti
            // (ux_incoming_source_ref). Yalnızca bu dosyayı atla; bu iç tx geri alınır,
            // dış döngü etkilenmez (REQUIRES_NEW).
            return Optional.empty();
        }
    }

    /**
     * Ham faturaları en yeni önce listeler; {@code status} verilirse o duruma göre filtreler.
     *
     * @param status null ise tümü; aksi halde yalnızca bu durumdakiler
     */
    @Transactional(readOnly = true)
    public List<IncomingInvoiceResponse> list(IncomingStatus status) {
        List<IncomingInvoice> rows = (status == null)
                ? repository.findAllByOrderByCreatedAtDescIdDesc()
                : repository.findByStatusOrderByCreatedAtDescIdDesc(status);
        return rows.stream().map(IncomingInvoiceResponse::from).toList();
    }

    /** Bir ham faturayı IGNORED işaretler. */
    @Transactional
    public IncomingInvoiceResponse ignore(Long id) {
        IncomingInvoice entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ham fatura bulunamadı: id=" + id));
        entity.setStatus(IncomingStatus.IGNORED);
        return IncomingInvoiceResponse.from(repository.save(entity));
    }
}
