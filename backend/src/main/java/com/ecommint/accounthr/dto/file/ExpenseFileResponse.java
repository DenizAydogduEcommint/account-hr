package com.ecommint.accounthr.dto.file;

import java.time.LocalDateTime;

import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E3-09 — Bir expense'e bağlı tek bir fatura dosyasının önizleme/indirme listesi metadata'sı.
 *
 * <p>Fiziksel yol ({@code filePath}) ASLA dışa verilmez; UI dosyaya yalnızca {@code id} ile
 * erişir ({@code GET /api/v1/files/{id}/preview} | {@code /download}). {@code previewable},
 * UI'a bir ipucudur: tarayıcıda satır-içi gösterilebilecek tipler (PDF/JPG/PNG) için
 * {@code true}; XML gibi ham metin için {@code false} (UI indirme önerir).
 *
 * @param id            FileAsset id (preview/download anahtarı)
 * @param fileName      görünen dosya adı
 * @param fileType      depolanan tip (PDF/XML/STATEMENT/RECEIPT/OTHER)
 * @param mimeType      depolanan MIME tipi (Content-Type kaynağı), null olabilir
 * @param sizeBytes     dosya boyutu (byte), null olabilir
 * @param uploadedAt    yükleme/oluşturma zamanı ({@code FileAsset.createdAt})
 * @param invoiceId     dosyanın bağlı olduğu invoice id (null olabilir)
 * @param invoiceStatus o invoice'un durumu (null olabilir)
 * @param previewable   tarayıcıda satır-içi gösterilebilir mi (PDF/JPG/PNG → true; XML → false)
 */
public record ExpenseFileResponse(
        Long id,
        String fileName,
        FileType fileType,
        String mimeType,
        Long sizeBytes,
        LocalDateTime uploadedAt,
        Long invoiceId,
        InvoiceStatus invoiceStatus,
        boolean previewable) {

    public static ExpenseFileResponse from(FileAsset asset) {
        var invoice = asset.getInvoice();
        return new ExpenseFileResponse(
                asset.getId(),
                asset.getFileName(),
                asset.getFileType(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                asset.getCreatedAt(),
                invoice != null ? invoice.getId() : null,
                invoice != null ? invoice.getStatus() : null,
                isPreviewable(asset));
    }

    /**
     * UI ipucu: tarayıcı bu dosyayı satır-içi (iframe/img) gösterebilir mi?
     * Karar önce MIME tipinden (application/pdf, image/*) verilir; MIME yoksa dosya
     * tipine düşülür (PDF → true; XML/diğer → false). XML/metin önizlenebilir SAYILMAZ
     * (UI indirme önerir; ham metni tarayıcı yine de gösterebilse de bu bir "belge önizleme"
     * değildir).
     */
    static boolean isPreviewable(FileAsset asset) {
        String mime = asset.getMimeType();
        if (mime != null) {
            String m = mime.toLowerCase(java.util.Locale.ROOT);
            if (m.equals("application/pdf")) {
                return true;
            }
            if (m.startsWith("image/")) {
                // image/jpeg, image/png, image/jpg vb. önizlenebilir.
                return true;
            }
            // application/xml, text/xml, text/* → önizlenebilir değil (indirilir).
            return false;
        }
        // MIME yoksa tipe düş: PDF önizlenebilir; XML/diğer değil.
        return asset.getFileType() == FileType.PDF;
    }
}
