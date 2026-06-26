package com.ecommint.accounthr.service.importer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.config.StorageProperties;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.importer.ReconciliationReport;
import com.ecommint.accounthr.dto.importer.ReconciliationReport.PeriodReconciliation;
import com.ecommint.accounthr.dto.importer.ReconciliationReport.PeriodStatus;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;

/**
 * E2-05 — Migrasyon doğrulama / mutabakat raporu (SALT-OKUNUR; DB'ye YAZMAZ).
 *
 * <p>E2-01..E2-04 migrasyonunun doğru aktarıldığını kanıtlar: her ay-sheet'in ana
 * {@code TOPLAM:} TL değeri ile DB'deki o period'un ana harcama TL toplamı (IGNORED
 * bilgi-amaçlı bölümler hariç) ±0.01 toleransla karşılaştırılır; satır/dosya/durum
 * sayıları denetlenir; her importer'ın idempotency anahtarı dokümante edilir.
 *
 * <p>Önemli kurallar (CLAUDE.md + DOD):
 * <ul>
 *   <li>Ana {@code TOPLAM:} satırı bilgi-amaçlı {@code MULTİNET TOPLAM:} / {@code SİGORTA
 *       TOPLAM:} satırlarından AYIRT EDİLİR; bu iki bilgi-toplamı ana mutabakata KATILMAZ.</li>
 *   <li>DB tarafı IGNORED durumundaki invoice'lara bağlı expense'leri (= Multinet/Sigorta)
 *       toplamdan hariç tutar — Excel ana TOPLAM'ı ile birebir eşlenir.</li>
 *   <li>Excel toplamı veya DB toplamı NULL/boş ise (ör. Nisan kısmi ay, {@code amount_try}
 *       girilmemiş) sonuç {@link PeriodStatus#WARNING}'dir, sert MISMATCH DEĞİL.</li>
 * </ul>
 *
 * <p>{@code @Transactional(readOnly = true)}: yöntem yalnızca okur; readOnly işareti hem
 * niyeti belgeler hem de yanlışlıkla yazımı önler.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    /** Excel ana TOPLAM ↔ DB toplam karşılaştırma toleransı (TL). */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    /** TL Karşılığı kolonu — ana TOPLAM değeri bu kolonda (12 kolon düzeni; 0-indexed = 5). */
    private static final int COL_AMOUNT_TRY = 5;

    /** Tarih kolonu (satır sınıflandırmasında bölüm-başlığı tespiti için). */
    private static final int COL_DATE = 0;

    /** Hizmet kolonu (ana harcama satırı sayımı için "Hizmet dolu mu" kontrolü). */
    private static final int COL_SERVICE = 1;

    /** Fatura Durumu kolonu. */
    private static final int COL_STATUS = 10;

    private static final int LAST_COL = 11;

    /**
     * Sheet adı (Türkçe ay) → period kodu. {@link ExcelImportService} / {@link StatusAuditService}
     * ile AYNI tanınan ay seti (tek kaynak ilkesi gevşek tutuldu; aynı dört ay).
     */
    private static final Map<String, String> MONTH_TO_PERIOD = new LinkedHashMap<>();

    static {
        MONTH_TO_PERIOD.put("Ocak", "2026-01");
        MONTH_TO_PERIOD.put("Şubat", "2026-02");
        MONTH_TO_PERIOD.put("Mart", "2026-03");
        MONTH_TO_PERIOD.put("Nisan", "2026-04");
    }

    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final PeriodRepository periodRepository;
    private final FileAssetRepository fileAssetRepository;
    private final StorageProperties storageProperties;

    public ReconciliationService(ExpenseRepository expenseRepository,
                                 InvoiceRepository invoiceRepository,
                                 PeriodRepository periodRepository,
                                 FileAssetRepository fileAssetRepository,
                                 StorageProperties storageProperties) {
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.periodRepository = periodRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.storageProperties = storageProperties;
    }

    /**
     * Yüklenen {@code 2026_Harcamalar.xlsx}'i DB ile mutabakat eder ve rapor döner.
     * Hiçbir yazma yapmaz.
     */
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(InputStream xlsx) {
        List<String> inconsistencies = new ArrayList<>();
        List<PeriodReconciliation> periodResults = new ArrayList<>();

        // 1) Excel ay-sheet'lerini parse et: ana TOPLAM TL + ana satır sayısı.
        Map<String, SheetTotals> sheetTotals = parseSheets(xlsx);

        // 2) Her tanınan period için Excel ↔ DB karşılaştır.
        boolean anyMismatch = false;
        for (Map.Entry<String, String> e : MONTH_TO_PERIOD.entrySet()) {
            String sheetName = e.getKey();
            String periodCode = e.getValue();
            SheetTotals totals = sheetTotals.get(sheetName);
            if (totals == null) {
                // Bu ay sheet'i yüklenen dosyada yok → sessizce atla (kısmi dosya olabilir).
                continue;
            }

            Period period = periodRepository.findByCode(periodCode).orElse(null);
            BigDecimal dbTotal = period != null
                    ? expenseRepository.sumMainAmountTryByPeriod(period.getId())
                    : null;
            long dbRowCount = period != null
                    ? expenseRepository.countMainExpensesByPeriod(period.getId())
                    : 0L;

            BigDecimal excelTotal = totals.amountTry;
            BigDecimal diff = (excelTotal != null && dbTotal != null)
                    ? excelTotal.subtract(dbTotal)
                    : null;

            PeriodStatus status = classifyPeriod(excelTotal, dbTotal, diff,
                    totals.rowCount, dbRowCount, periodCode, inconsistencies);
            if (status == PeriodStatus.MISMATCH) {
                anyMismatch = true;
            }

            periodResults.add(new PeriodReconciliation(
                    periodCode, sheetName, excelTotal, dbTotal, diff,
                    totals.rowCount, dbRowCount, status));
        }

        // 3) Dosya mutabakatı (fiziksel ↔ files DB). countPhysicalFiles tarama
        //    başarısızlığında -1 (sentinel) döner; bu durumda mutabakat ABORT EDİLMEZ,
        //    yalnızca bir tutarsızlık notu düşülür (salt-okunur rapor MISMATCH değil).
        long filesDbRows = fileAssetRepository.count();
        long filesPhysical = countPhysicalFiles();
        // API'ye sızan değer: tarama başarısızsa null (sentinel/-1 sızdırılmaz).
        Long filesPhysicalReported = filesPhysical < 0 ? null : filesPhysical;
        if (filesPhysical < 0) {
            inconsistencies.add(String.format(Locale.ROOT,
                    "Dosya: fiziksel dosya sayısı belirlenemedi (storage kökü taranamadı) — "
                            + "DB %d kayıt; tarama hatası incelenmeli (MISMATCH değildir).",
                    filesDbRows));
        } else if (filesPhysical > filesDbRows) {
            inconsistencies.add(String.format(Locale.ROOT,
                    "Dosya: fiziksel %d > DB %d — fark E2-03 SHA-256 dedup'ından kaynaklanabilir "
                            + "(içerik-duplicate dosyalar tek kayda indirgenir); MISMATCH değildir.",
                    filesPhysical, filesDbRows));
        } else if (filesPhysical < filesDbRows) {
            inconsistencies.add(String.format(Locale.ROOT,
                    "Dosya: fiziksel %d < DB %d — storage kökü tarama anında eksik dosya "
                            + "içeriyor olabilir; incelenmeli.",
                    filesPhysical, filesDbRows));
        }

        // 4) Durum dağılımı (E2-04 ile tutarlı; aynı repo sorgusu).
        Map<InvoiceStatus, Long> statusDistribution = statusDistribution();

        // 5) Idempotency anahtarları (dokümantasyon — importer YENİDEN çalıştırılmaz).
        Map<String, String> idempotencyKeys = idempotencyKeys();

        boolean ok = !anyMismatch;
        return new ReconciliationReport(
                periodResults, filesPhysicalReported, filesDbRows, statusDistribution,
                idempotencyKeys, inconsistencies, ok);
    }

    // ---------------------------------------------------------------------------
    // Period sınıflandırma
    // ---------------------------------------------------------------------------

    private PeriodStatus classifyPeriod(BigDecimal excelTotal, BigDecimal dbTotal, BigDecimal diff,
                                        int excelRowCount, long dbRowCount, String periodCode,
                                        List<String> inconsistencies) {
        // Tutar NULL/boş → WARNING (ör. Nisan kısmi ay; tutar girilmemiş).
        if (excelTotal == null || dbTotal == null) {
            inconsistencies.add(String.format(Locale.ROOT,
                    "%s: tutar mutabakatı yapılamadı — Excel TOPLAM=%s, DB toplam=%s "
                            + "(tutar girilmemiş/kısmi ay); WARNING.",
                    periodCode, str(excelTotal), str(dbTotal)));
            // Satır sayısı yine de raporlanır; uyumsuzluğu da not düş ama yine WARNING kalsın.
            if (excelRowCount != dbRowCount) {
                inconsistencies.add(String.format(Locale.ROOT,
                        "%s: satır sayısı tutmuyor — Excel=%d, DB=%d (WARNING period'unda).",
                        periodCode, excelRowCount, dbRowCount));
            }
            return PeriodStatus.WARNING;
        }

        boolean amountOk = diff.abs().compareTo(TOLERANCE) <= 0;
        boolean rowOk = excelRowCount == dbRowCount;

        if (!amountOk) {
            inconsistencies.add(String.format(Locale.ROOT,
                    "%s: TUTAR tutmuyor — Excel TOPLAM=%s, DB toplam=%s, fark=%s (tolerans ±%s).",
                    periodCode, str(excelTotal), str(dbTotal), str(diff), TOLERANCE.toPlainString()));
        }
        if (!rowOk) {
            inconsistencies.add(String.format(Locale.ROOT,
                    "%s: SATIR sayısı tutmuyor — Excel=%d, DB=%d.",
                    periodCode, excelRowCount, dbRowCount));
        }
        return (amountOk && rowOk) ? PeriodStatus.MATCH : PeriodStatus.MISMATCH;
    }

    // ---------------------------------------------------------------------------
    // Excel parse — ana TOPLAM + ana satır sayısı
    // ---------------------------------------------------------------------------

    private Map<String, SheetTotals> parseSheets(InputStream xlsx) {
        Map<String, SheetTotals> result = new LinkedHashMap<>();
        // DataFormatter POI'de thread-safe değil → çağrı başına lokal örnek.
        DataFormatter dataFormatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(xlsx)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName() != null ? sheet.getSheetName().trim() : "";
                if (!MONTH_TO_PERIOD.containsKey(sheetName)) {
                    continue; // Servisler ve tanınmayan sheet'ler atlanır.
                }
                result.put(sheetName, parseSheet(sheet, dataFormatter));
            }
        } catch (IOException e) {
            throw new ExcelImportException("Excel dosyası okunamadı.", e);
        }
        return result;
    }

    /**
     * Tek sheet'ten ana {@code TOPLAM:} TL değerini (col 6) ve ana harcama satırı sayısını
     * (TOPLAM/bilgi-bölümü/footer hariç) çıkarır.
     */
    private SheetTotals parseSheet(Sheet sheet, DataFormatter dataFormatter) {
        BigDecimal mainTotal = null;
        int rowCount = 0;
        boolean informational = false; // bölüm-başlığından sonra true

        int lastRow = sheet.getLastRowNum();
        for (int r = 1; r <= lastRow; r++) { // row 0 = header
            Row row = sheet.getRow(r);
            if (isRowEmpty(row, dataFormatter)) {
                continue;
            }

            String label = rowTotalLabel(row, dataFormatter);
            if (label != null) {
                // Herhangi bir TOPLAM satırı: ana mı, bilgi-amaçlı mı?
                if (isMainTotalLabel(label)) {
                    BigDecimal v = getDecimal(row, COL_AMOUNT_TRY, dataFormatter);
                    if (mainTotal == null) {
                        mainTotal = v; // ilk ana TOPLAM otoriterdir
                    }
                }
                // TOPLAM (ana veya bilgi) satırı asla harcama satırı sayılmaz.
                continue;
            }

            // Bölüm başlığı (Multinet/Sağlık Sigortası), gerisi boş → sonrası bilgi-amaçlı.
            String firstCol = getString(row, COL_DATE, dataFormatter).trim();
            if (isSectionHeaderLabel(firstCol) && isRestEmpty(row, COL_DATE, dataFormatter)) {
                informational = true;
                continue;
            }

            // Veri satırı: ana harcama mı? Bilgi-amaçlı bölümdeki satırlar ana sayıma girmez.
            if (informational) {
                continue;
            }
            String serviceName = getString(row, COL_SERVICE, dataFormatter).trim();
            if (serviceName.isEmpty()) {
                // Hizmet boş → güvenli atla (gerçek bir harcama satırı değil).
                continue;
            }
            rowCount++;
        }

        return new SheetTotals(mainTotal, rowCount);
    }

    /**
     * Satırda bir {@code ... TOPLAM:} etiketi varsa onu döner (trim'li), yoksa {@code null}.
     * Etiket {@code MULTİNET TOPLAM:} / {@code SİGORTA TOPLAM:} (bilgi-amaçlı) veya sade
     * {@code TOPLAM:} (ana) olabilir; ayrımı {@link #isMainTotalLabel(String)} yapar.
     */
    static String rowTotalLabel(Row row, DataFormatter dataFormatter) {
        if (row == null) {
            return null;
        }
        for (int c = 0; c <= LAST_COL; c++) {
            String v = cellText(row, c, dataFormatter).trim();
            String upper = v.toUpperCase(Locale.forLanguageTag("tr"));
            if (upper.contains("TOPLAM:")) {
                return v;
            }
        }
        return null;
    }

    /**
     * Bir TOPLAM etiketi ANA harcama toplamı mı? Bilgi-amaçlı (Multinet/Sigorta) toplam
     * satırları HARİÇ tutulur; yalnızca sade/ana {@code TOPLAM:} kabul edilir.
     *
     * <p>Kural: etiket {@code MULTİNET} veya {@code SİGORTA} (büyük/küçük + i/İ duyarsız)
     * içeriyorsa ana DEĞİLDİR. Geriye kalan {@code TOPLAM:} satırı anadır.
     */
    static boolean isMainTotalLabel(String label) {
        if (label == null) {
            return false;
        }
        String upper = label.toUpperCase(Locale.forLanguageTag("tr"));
        if (!upper.contains("TOPLAM:")) {
            return false;
        }
        // Türkçe i/İ ve olası ascii varyantlarına karşı toleranslı kontrol.
        boolean informational = upper.contains("MULTİNET") || upper.contains("MULTINET")
                || upper.contains("SİGORTA") || upper.contains("SIGORTA");
        return !informational;
    }

    /** Sheet adı (Türkçe ay) → period kodu; tanınmıyorsa {@code null}. */
    static String monthNameToPeriodCode(String sheetName) {
        if (sheetName == null) {
            return null;
        }
        return MONTH_TO_PERIOD.get(sheetName.trim());
    }

    /**
     * Bölüm başlığı mı? ({@code Multinet Yemek Kartı...} / {@code Sağlık Sigortası...}).
     * Importer (E2-01) ile AYNI eşleme kuralını kullanmak için tek kaynak
     * {@link SectionHeaderText}'e delege eder — iki taraf aynı satırları bilgi-amaçlı sayar,
     * böylece reconciler sahte bir MISMATCH üretmez.
     */
    private boolean isSectionHeaderLabel(String label) {
        return SectionHeaderText.isSectionHeaderLabel(label);
    }

    private boolean isRowEmpty(Row row, DataFormatter dataFormatter) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c <= LAST_COL; c++) {
            if (!cellText(row, c, dataFormatter).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isRestEmpty(Row row, int exceptCol, DataFormatter dataFormatter) {
        for (int c = 0; c <= LAST_COL; c++) {
            if (c == exceptCol) {
                continue;
            }
            if (!cellText(row, c, dataFormatter).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------
    // Dosya / durum / idempotency
    // ---------------------------------------------------------------------------

    /**
     * Storage kökü altındaki fiziksel (normal) dosya sayısı. Kök yok/tanımsızsa 0;
     * {@code waiting/} ve {@code trash/} dahil tüm normal dosyalar sayılır (E2-03
     * kopyalanan dosyalar). Tarama I/O hatasıyla başarısız olursa salt-okunur mutabakatı
     * çökertmemek için {@code -1} (sentinel) döner; çağıran bunu tutarsızlık notuna çevirir.
     */
    private long countPhysicalFiles() {
        String root = storageProperties.getRoot();
        if (root == null || root.isBlank()) {
            return 0L;
        }
        Path rootPath = Paths.get(root);
        if (!Files.isDirectory(rootPath)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(rootPath)) {
            // Yarım kalmış atomik-yazma artıkları (.upload-*.tmp / .copy-*.tmp) gerçek
            // kalıcı dosya DEĞİLDİR → sayıma girmemeli. Aksi halde fiziksel sayı şişer ve
            // tutarsızlık notu yanlışlıkla SHA-256 dedup'ını suçlardı.
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !isTempFile(p))
                    .count();
        } catch (IOException e) {
            log.warn("Storage kökü taranamadı ({}): {} — fiziksel dosya sayısı belirlenemedi.",
                    root, e.getMessage());
            return -1L; // sentinel: tarama başarısız (mutabakat abort edilmez)
        }
    }

    /** Atomik-yazma geçici dosyası mı? ({@code .upload-<...>.tmp} / {@code .copy-<...>.tmp}). */
    private static boolean isTempFile(Path p) {
        return p.getFileName().toString().matches("\\.(upload|copy)-.+\\.tmp");
    }

    private Map<InvoiceStatus, Long> statusDistribution() {
        Map<InvoiceStatus, Long> distribution = new EnumMap<>(InvoiceStatus.class);
        for (Object[] row : invoiceRepository.countGroupByStatus()) {
            InvoiceStatus status = (InvoiceStatus) row[0];
            Long count = ((Number) row[1]).longValue();
            if (status != null) {
                distribution.put(status, count);
            }
        }
        return distribution;
    }

    /**
     * Her importer'ın doğal/idempotency anahtarı (dokümantasyon). Re-import 0 yeni satır
     * üretir (E2-01..03'te kanıtlandı); burada yalnızca anahtarlar belgelenir.
     */
    private Map<String, String> idempotencyKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("expense (E2-01)",
                "source_row_hash — SHA-256(period|hizmet|currency|amount|amountTry|tarih) "
                        + "+ pass-içi kopya-sayacı son-eki; aynı hash varsa satır atlanır "
                        + "(fiziksel satır konumundan bağımsız → re-import pozisyona stabil).");
        keys.put("service (E2-02)",
                "normalize(hizmet) + provider — büyük/küçük harf duyarsız UPSERT; "
                        + "eşleşen servis güncellenir, yenisi oluşturulmaz.");
        keys.put("file (E2-03)",
                "sha256 (dosya içeriği) — aynı içerikli dosya tek kayda indirgenir (dedup); "
                        + "ayrıca storage-root göreli file_path tekrar import'ta atlanır.");
        return keys;
    }

    // ---------------------------------------------------------------------------
    // Hücre yardımcıları
    // ---------------------------------------------------------------------------

    private static String cellText(Row row, int col, DataFormatter dataFormatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell);
    }

    private String getString(Row row, int col, DataFormatter dataFormatter) {
        return cellText(row, col, dataFormatter);
    }

    private BigDecimal getDecimal(Row row, int col, DataFormatter dataFormatter) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String raw = cellText(row, col, dataFormatter).trim();
        if (raw.isEmpty()) {
            return null;
        }
        String cleaned = raw.replace("₺", "").replace("$", "").replace("€", "").replace(" ", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("TOPLAM tutarı parse edilemedi: '{}'", raw);
            return null;
        }
    }

    private String str(BigDecimal v) {
        return v == null ? "(boş)" : v.toPlainString();
    }

    /** Tek sheet'in ana TOPLAM TL'si + ana harcama satırı sayısı. */
    private record SheetTotals(BigDecimal amountTry, int rowCount) {
    }
}
