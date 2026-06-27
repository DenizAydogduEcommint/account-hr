package com.ecommint.accounthr.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.dto.statement.StatementBatchActionRequest;
import com.ecommint.accounthr.dto.statement.StatementBatchActionResponse;
import com.ecommint.accounthr.dto.statement.StatementPreviewResponse;
import com.ecommint.accounthr.service.statement.StatementUploadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * E4-01 — Banka ekstresi yükleme + parse + onay uçları. Ekstre yükleme bir muhasebe/yönetici
 * görevidir (ekip üyesi DEĞİL): {@code hasAnyRole('ADMIN','ACCOUNTING')}.
 *
 * <p>Akış: upload (önizleme, PENDING satırlar) → confirm (CONFIRMED) ya da discard (DISCARDED).
 * İş mantığı + idempotency {@link StatementUploadService}'de; controller ince kalır. Gerçek
 * banka-özgül satır çıkarımı henüz placeholder'dır (örnek ekstre bekleniyor — E4-01); akış
 * boş işlem listesi + uyarı ile yine de uçtan uca çalışır.
 */
@RestController
@RequestMapping("/api/v1/statements")
@Tag(name = "Statements", description = "Banka ekstresi yükle → önizle → onayla (E4-01)")
@SecurityRequirement(name = "bearerAuth")
public class StatementController {

    private final StatementUploadService statementUploadService;

    public StatementController(StatementUploadService statementUploadService) {
        this.statementUploadService = statementUploadService;
    }

    /**
     * Bir ekstre yükler → parse eder → PENDING ham işlemler oluşturur → önizleme döner.
     *
     * @param file      zorunlu — ekstre dosyası (.xlsx/.xls/.docx; .doc/.pdf → uyarı)
     * @param cardLast4 zorunlu — kartın son 4 hanesi (bilinmeyen → 400)
     * @param month     zorunlu — {@code YYYY-MM} (biçimsiz → 400)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(
            summary = "Ekstre yükle (önizleme)",
            description = "Multipart: file + cardLast4 + month (YYYY-MM). Dosya saklanır, SHA-256 "
                    + "hesaplanır (batchRef + idempotency). Aynı (sha256+kart+dönem) için CONFIRMED "
                    + "batch varsa alreadyUploaded=true döner (mükerrer yok). Aksi halde parse edilip "
                    + "PENDING ham işlemler yazılır. Banka satır çıkarımı henüz placeholder (boş liste "
                    + "+ uyarı). Yalnızca ADMIN/ACCOUNTING.")
    public StatementPreviewResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("cardLast4") String cardLast4,
            @RequestParam("month") String month) {
        return statementUploadService.upload(file, cardLast4, month);
    }

    /** Bir batch'in PENDING satırlarını CONFIRMED yapar. */
    @PostMapping(path = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(summary = "Batch'i onayla", description = "batchRef'in PENDING satırlarını CONFIRMED yapar.")
    public StatementBatchActionResponse confirm(@Valid @RequestBody StatementBatchActionRequest request) {
        return statementUploadService.confirm(request.batchRef());
    }

    /** Bir batch'in PENDING satırlarını DISCARDED yapar (opsiyonel). */
    @PostMapping(path = "/discard", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(summary = "Batch'i reddet", description = "batchRef'in PENDING satırlarını DISCARDED yapar.")
    public StatementBatchActionResponse discard(@Valid @RequestBody StatementBatchActionRequest request) {
        return statementUploadService.discard(request.batchRef());
    }

    /** Bir batch'in işlemlerini yeniden önizleme için listeler. */
    @GetMapping(path = "/{batchRef}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(summary = "Batch önizlemesi", description = "batchRef'in tüm ham işlemlerini döner.")
    public ResponseEntity<StatementPreviewResponse> getBatch(@PathVariable String batchRef) {
        return ResponseEntity.status(HttpStatus.OK).body(statementUploadService.getBatch(batchRef));
    }
}
