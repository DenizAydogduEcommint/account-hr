package com.ecommint.accounthr.dto.file;

import java.time.LocalDateTime;

import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.enums.FileType;

/**
 * Fatura dosyası metadata yanıtı (E1-04). Fiziksel içerik döndürülmez; indirme
 * ayrı uçtan (GET /api/files/{id}/download) yapılır.
 */
public record FileResponse(
        Long id,
        Long invoiceId,
        String filePath,
        String fileName,
        FileType fileType,
        String mimeType,
        Long sizeBytes,
        String sha256,
        LocalDateTime createdAt) {

    public static FileResponse from(FileAsset asset) {
        return new FileResponse(
                asset.getId(),
                asset.getInvoice() != null ? asset.getInvoice().getId() : null,
                asset.getFilePath(),
                asset.getFileName(),
                asset.getFileType(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                asset.getSha256(),
                asset.getCreatedAt());
    }
}
