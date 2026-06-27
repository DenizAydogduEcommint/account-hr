package com.ecommint.accounthr.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.domain.enums.IncomingStatus;
import com.ecommint.accounthr.dto.incoming.IncomingInvoiceResponse;
import com.ecommint.accounthr.dto.incoming.IncomingPullResult;
import com.ecommint.accounthr.service.incoming.DriveWaitingPullService;
import com.ecommint.accounthr.service.incoming.IncomingInvoiceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * E5-02 — Ham/gelen fatura (incoming invoice) uçları: Drive {@code waiting/} pull + ingest +
 * listeleme. Toplama bir muhasebe/yönetici görevidir: {@code hasAnyRole('ADMIN','ACCOUNTING')}.
 *
 * <p>Bu round YALNIZCA PULL + INGEST: dosyalar Drive waiting/'den lokal storage'a KOPYALANIR
 * (rclone copy) ve her dosya için bir ham fatura kaydı oluşturulur. Drive'a push/delete YOKTUR
 * ve ay klasörüne taşıma/waiting silme E5-04'e ertelenmiştir.
 *
 * <p>rclone erişilemez/yok ise pull {@link com.ecommint.accounthr.service.incoming.RcloneException}
 * fırlatır → {@link GlobalExceptionHandler} 502 BAD_GATEWAY'e çevirir (asla 500 değil).
 */
@RestController
@RequestMapping("/api/v1/incoming")
@Tag(name = "Incoming Invoices", description = "Ham fatura: Drive waiting/ pull + ingest (E5-02)")
@SecurityRequirement(name = "bearerAuth")
public class IncomingInvoiceController {

    private final DriveWaitingPullService pullService;
    private final IncomingInvoiceService incomingInvoiceService;

    public IncomingInvoiceController(DriveWaitingPullService pullService,
                                     IncomingInvoiceService incomingInvoiceService) {
        this.pullService = pullService;
        this.incomingInvoiceService = incomingInvoiceService;
    }

    /** Drive {@code waiting/}'i lokale çek (rclone copy) ve yeni dosyaları ham fatura olarak kaydet. */
    @PostMapping(path = "/pull")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(
            summary = "Drive waiting/ klasörünü çek + ham fatura olarak kaydet",
            description = "gdrive-ecommint:faturalar/waiting/ dizinini lokal landing dizinine çeker "
                    + "(rclone copy; yalnızca remote→local PULL — Drive'a ASLA push/delete yok), her "
                    + "yeni dosya için bir ham fatura (NEW) oluşturur. Idempotent: aynı (kaynak, "
                    + "kaynak-referansı) veya aynı SHA-256 atlanır. Özet döner. ADMIN/ACCOUNTING.")
    public IncomingPullResult pull() {
        return pullService.pull();
    }

    /** Ham faturaları listele (en yeni önce); {@code status} ile filtrelenebilir. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(
            summary = "Ham faturaları listele",
            description = "En yeni önce. Opsiyonel ?status=NEW|MATCHED|IGNORED filtresi. ADMIN/ACCOUNTING.")
    public List<IncomingInvoiceResponse> list(
            @RequestParam(name = "status", required = false) IncomingStatus status) {
        return incomingInvoiceService.list(status);
    }

    /** Bir ham faturayı IGNORED işaretle. */
    @PatchMapping(path = "/{id}/ignore")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTING')")
    @Operation(
            summary = "Ham faturayı yok say (IGNORED)",
            description = "Belirtilen ham faturanın durumunu IGNORED yapar. ADMIN/ACCOUNTING.")
    public IncomingInvoiceResponse ignore(@PathVariable Long id) {
        return incomingInvoiceService.ignore(id);
    }
}
