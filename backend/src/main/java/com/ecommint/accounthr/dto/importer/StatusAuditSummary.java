package com.ecommint.accounthr.dto.importer;

import java.util.List;
import java.util.Map;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E2-04 — Fatura durumu/renk tutarlılık denetimi özet raporu.
 *
 * <p>İki bağımsız denetimi birleştirir:
 * <ul>
 *   <li><b>A bölümü (Excel metin/renk):</b> her ay-sheet veri satırının {@code Fatura
 *       Durumu} hücresindeki METİN ile DOLGU RENGİ aynı duruma çözülüyor mu? Çelişirse
 *       (FF9800 Araştırılacak↔Ignored belirsizliği hariç) {@code textColorMismatch}
 *       sayılır ve {@code mismatches} listesine eklenir. Metin önceliklidir.</li>
 *   <li><b>B bölümü (DB dosya/durum):</b> E2-03'te en az bir {@code FileAsset} bağlanmış
 *       ama durumu hâlâ {@link InvoiceStatus#EXPECTED} (Bekleniyor) kalmış invoice'lar
 *       tutarsızdır ("dosya bulundu ama Bekleniyor"). {@code autofix=true} ise durum
 *       {@link InvoiceStatus#FOUND}'a çekilir ve {@code fileStatusFixed} sayılır.</li>
 * </ul>
 *
 * <p>Alanlar:
 * <ul>
 *   <li>{@code statusDistribution}: DB'deki invoice'ların durum→adet dağılımı.</li>
 *   <li>{@code textColorMismatch}: Excel metin-renk çelişkisi sayısı (A).</li>
 *   <li>{@code fileStatusInconsistent}: dosyası olduğu hâlde EXPECTED kalan invoice sayısı (B).</li>
 *   <li>{@code fileStatusFixed}: autofix ile FOUND'a çekilen invoice sayısı (autofix=false → 0).</li>
 *   <li>{@code undefinedStatus}: durumu null kalan invoice sayısı (E2-01 garantisiyle 0 beklenir).</li>
 *   <li>{@code mismatches}: metin-renk çelişki detayları (A).</li>
 *   <li>{@code fileStatusInconsistencies}: dosya/durum tutarsızlık detayları (B).</li>
 * </ul>
 */
public record StatusAuditSummary(
        Map<InvoiceStatus, Long> statusDistribution,
        int textColorMismatch,
        int fileStatusInconsistent,
        int fileStatusFixed,
        int undefinedStatus,
        List<TextColorMismatch> mismatches,
        List<FileStatusInconsistency> fileStatusInconsistencies) {

    /**
     * Excel'de bir veri satırının metni ve rengi farklı duruma çözüldü (A bölümü).
     *
     * @param sheet         sheet adı
     * @param row           1-tabanlı satır numarası (Excel kullanıcı görünümü)
     * @param text          hücredeki ham metin
     * @param textStatus    metinden çözülen durum (otoriter)
     * @param colorHex      hücre dolgu rengi (RGB hex)
     * @param colorStatus   renkten çözülen durum
     */
    public record TextColorMismatch(
            String sheet,
            int row,
            String text,
            InvoiceStatus textStatus,
            String colorHex,
            InvoiceStatus colorStatus) {
    }

    /**
     * Dosyası olduğu hâlde EXPECTED kalan invoice (B bölümü).
     *
     * @param invoiceId   invoice id
     * @param fileCount   bağlı dosya sayısı
     * @param oldStatus   önceki durum (her zaman EXPECTED)
     * @param newStatus   autofix sonrası durum (autofix=false → EXPECTED ile aynı)
     * @param fixed       bu invoice autofix ile düzeltildi mi
     */
    public record FileStatusInconsistency(
            Long invoiceId,
            int fileCount,
            InvoiceStatus oldStatus,
            InvoiceStatus newStatus,
            boolean fixed) {
    }
}
