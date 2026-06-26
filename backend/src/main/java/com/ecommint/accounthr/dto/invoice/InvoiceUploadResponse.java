package com.ecommint.accounthr.dto.invoice;

import java.util.List;

import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E3-05 — Fatura yükleme sonucu özeti. Yüklenen dosyalar + bağlandıkları
 * invoice/expense ve oluşan/güncellenen durum döner.
 *
 * @param invoiceId yükleme sonucu güncellenen/oluşturulan invoice id
 * @param expenseId invoice'un bağlı olduğu expense id (var olan ya da yeni)
 * @param status    invoice'un son durumu (FOUND ya da E_INVOICE)
 * @param expenseCreated bu yükleme yeni bir expense oluşturduysa true (var olanı kullandıysa false)
 * @param files     depolanan dosyaların özeti
 */
public record InvoiceUploadResponse(
        Long invoiceId,
        Long expenseId,
        InvoiceStatus status,
        boolean expenseCreated,
        List<StoredFileSummary> files) {

    /**
     * Tek bir depolanan dosyanın özeti.
     *
     * @param fileAssetId DB'deki FileAsset id'si
     * @param fileName    üretilen dosya adı (ör. {@code google_workspace_subat.pdf})
     * @param filePath    storage köküne göreli yol (ör. {@code 2026-02/google_workspace_subat.pdf})
     * @param fileType    tespit edilen dosya tipi
     * @param sizeBytes   byte cinsinden boyut
     */
    public record StoredFileSummary(
            Long fileAssetId,
            String fileName,
            String filePath,
            FileType fileType,
            Long sizeBytes) {
    }
}
