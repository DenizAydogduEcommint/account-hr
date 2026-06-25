package com.ecommint.accounthr.service.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.importer.StatusAuditSummary;
import com.ecommint.accounthr.dto.importer.StatusAuditSummary.TextColorMismatch;

/**
 * E2-04 — Fatura durumu/renk enum migrasyonunun TUTARLILIK DENETİMİ.
 *
 * <p>E2-01 zaten {@code Fatura Durumu} METNİNİ okuyup {@code invoice.status}'u set etti.
 * Bu servis bunun ÜZERİNE iki bağımsız denetim ekler:
 * <ol>
 *   <li><b>A — Excel metin/renk tutarlılığı</b> (salt-okunur, DB gerektirmez): her ay-sheet
 *       veri satırı için {@code Fatura Durumu} hücresinin METNİ ({@link StatusText}) ve DOLGU
 *       RENGİ ({@link StatusColors}) ayrı ayrı duruma çözülür. İkisi de çözülüp ÇELİŞİRSE
 *       ({@code FF9800} Araştırılacak↔Ignored belirsizliği hariç) bir {@code textColorMismatch}
 *       sayılır. Metin otoriterdir.</li>
 *   <li><b>B — Dosya/durum tutarlılığı</b> (DB): E2-03'te en az bir {@code FileAsset} bağlanmış
 *       ama hâlâ {@link InvoiceStatus#EXPECTED} kalan invoice'lar tutarsızdır. {@code autofix}
 *       açıksa durum {@link InvoiceStatus#FOUND}'a (note/durumda e-Fatura işareti varsa
 *       {@link InvoiceStatus#E_INVOICE}) çekilir. Bu bölüm ayrı, normal {@code @Transactional}
 *       bir servise ({@link StatusAuditDbService}) delege edilir.</li>
 * </ol>
 *
 * <p>Varsayılan davranış: yalnızca RAPORLA (autofix=false, manuel onay — DOD önerisi).
 */
@Service
public class StatusAuditService {

    /** Sheet adı (Türkçe ay) → period kodu. ExcelImportService ile aynı tanınan ay seti. */
    private static final Map<String, String> MONTH_TO_PERIOD = Map.of(
            "Ocak", "2026-01",
            "Şubat", "2026-02",
            "Mart", "2026-03",
            "Nisan", "2026-04");

    /** Fatura Durumu kolonu (E2-01 ile aynı 12 kolon düzeni; 0-indexed). */
    private static final int COL_STATUS = 10;

    /**
     * B bölümünün DB okuma + autofix mantığı. Ayrı bir {@code @Service} olduğundan
     * {@code @Transactional} metodu gerçek bir proxy üzerinden çağrılır; eski {@code @Lazy}
     * öz-enjeksiyon kırılganlığı yok.
     */
    private final StatusAuditDbService dbService;

    public StatusAuditService(StatusAuditDbService dbService) {
        this.dbService = dbService;
    }

    /**
     * Durum/renk denetimini çalıştırır ve özet döner.
     *
     * <p>Bilinçli olarak {@code @Transactional} DEĞİL: A bölümü (Excel parse) saf I/O'dur
     * ve transaction açmadan, DB bağlantısını boşa tutmadan çalışır. DB mutasyonu yalnızca
     * {@code autofix=true} iken {@link StatusAuditDbService#auditFileStatus(boolean)} içinde,
     * ayrı bir transaction'da yapılır.
     *
     * @param xlsx    {@code 2026_Harcamalar.xlsx} stream'i (A bölümü için okunur).
     * @param autofix true ise B bölümündeki tutarsız invoice'lar düzeltilir; false ise
     *                yalnızca raporlanır (varsayılan, manuel onay).
     */
    public StatusAuditSummary auditStatuses(InputStream xlsx, boolean autofix) {
        // --- A: Excel metin/renk tutarlılığı (salt-okunur, DB GEREKTİRMEZ) ---
        List<TextColorMismatch> mismatches = auditTextColor(xlsx);

        // --- B: dosya/durum tutarlılığı (DB; mutasyon ayrı transaction'da) ---
        return dbService.auditFileStatus(autofix).toSummary(mismatches);
    }

    // ---------------------------------------------------------------------------
    // A — Excel metin/renk tutarlılığı
    // ---------------------------------------------------------------------------

    private List<TextColorMismatch> auditTextColor(InputStream xlsx) {
        List<TextColorMismatch> mismatches = new ArrayList<>();
        // DataFormatter POI'de thread-safe değil → çağrı başına lokal örnek.
        DataFormatter dataFormatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(xlsx)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName() != null ? sheet.getSheetName().trim() : "";
                if (!MONTH_TO_PERIOD.containsKey(sheetName)) {
                    continue; // Servisler ve tanınmayan sheet'ler atlanır.
                }
                auditSheet(sheet, sheetName, dataFormatter, mismatches);
            }
        } catch (IOException e) {
            throw new ExcelImportException("Excel dosyası okunamadı.", e);
        }
        return mismatches;
    }

    private void auditSheet(Sheet sheet, String sheetName, DataFormatter dataFormatter,
                            List<TextColorMismatch> mismatches) {
        int lastRow = sheet.getLastRowNum();
        // Row 0 = header; veri row 1'den.
        for (int r = 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Cell statusCell = row.getCell(COL_STATUS);
            if (statusCell == null) {
                continue;
            }
            String text = dataFormatter.formatCellValue(statusCell).trim();
            InvoiceStatus textStatus = StatusText.toStatusOrNull(text);
            String colorHex = readFillRgbHex(statusCell);
            InvoiceStatus colorStatus = StatusColors.fromHex(colorHex);

            // Her ikisi de çözülmeli; biri çözülemiyorsa çelişki sayılmaz.
            if (textStatus == null || colorStatus == null) {
                continue;
            }
            // FF9800 (Araştırılacak↔Ignored) belirsizliği: ayrım metinle yapılır,
            // bu yüzden metin bu iki durumdan biriyse VE renk de turuncuysa çelişki YOK.
            if (StatusColors.isAmbiguousColor(colorHex)
                    && (textStatus == InvoiceStatus.TO_INVESTIGATE
                        || textStatus == InvoiceStatus.IGNORED)) {
                continue;
            }
            if (textStatus != colorStatus) {
                mismatches.add(new TextColorMismatch(
                        sheetName, r + 1, text, textStatus,
                        StatusColors.normalizeToRgb(colorHex), colorStatus));
            }
        }
    }

    /**
     * Hücre dolgu (solid fill) rengini RGB hex olarak okur. Yalnızca DOĞRUDAN (theme/
     * koşullu-biçimlendirme değil) solid foreground fill desteklenir. Renk yoksa
     * {@code null}.
     */
    private String readFillRgbHex(Cell cell) {
        CellStyle style = cell.getCellStyle();
        if (style == null || style.getFillPattern() != FillPatternType.SOLID_FOREGROUND) {
            return null;
        }
        if (!(style.getFillForegroundColorColor() instanceof XSSFColor color)) {
            return null;
        }
        String argb = color.getARGBHex(); // 8 hane (00RRGGBB / FFRRGGBB) veya null.
        if (argb == null) {
            return null;
        }
        return StatusColors.normalizeToRgb(argb);
    }
}
