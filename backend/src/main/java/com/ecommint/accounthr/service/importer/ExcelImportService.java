package com.ecommint.accounthr.service.importer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.importer.ImportSummary;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E2-01 — {@code 2026_Harcamalar.xlsx} ay-sheet'lerini {@code expenses} (+ taslak
 * {@code invoices}) tablosuna aktaran importer.
 *
 * <p>Her ay sheet'i ayrı işlenir; {@code Servisler} sheet'i atlanır (E2-02). Satırlar
 * sınıflandırılır (boş / TOPLAM / bölüm-başlığı / veri) ve bölüm-başlığı sonrası gelen
 * veri satırları {@code informational=true} olarak işaretlenir. Idempotency için her veri
 * satırından stabil SHA-256 üretilir; aynı hash daha önce eklenmişse satır atlanır.
 *
 * <p>Servis çözümleme zorunludur: Provider isimle bulunur/oluşturulur, Service ise
 * (sağlayıcı + normalize isim) ile bulunur/oluşturulur (minimal). Kart son-4 ile bağlanır
 * (boşsa null).
 */
@Service
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    /** Sheet adı (Türkçe ay) → period kodu. Servisler ve diğerleri atlanır. */
    private static final Map<String, String> MONTH_TO_PERIOD = Map.of(
            "Ocak", "2026-01",
            "Şubat", "2026-02",
            "Mart", "2026-03",
            "Nisan", "2026-04");

    // 12 kolon sırası (header row 1, data row 2+).
    private static final int COL_DATE = 0;          // Tarih
    private static final int COL_SERVICE = 1;        // Hizmet
    private static final int COL_PROVIDER = 2;       // Sağlayıcı
    private static final int COL_AMOUNT = 3;         // Tutar
    private static final int COL_CURRENCY = 4;       // Para Birimi
    private static final int COL_AMOUNT_TRY = 5;     // TL Karşılığı
    private static final int COL_CARD = 6;           // Kart
    private static final int COL_TEAM = 7;           // Kullanan Takım (importer'da kullanılmaz)
    private static final int COL_PURPOSE = 8;        // Amaç
    private static final int COL_ACC_EMAIL = 9;      // Muhasebe E-posta (importer'da kullanılmaz)
    private static final int COL_STATUS = 10;        // Fatura Durumu
    private static final int COL_NOTE = 11;          // Fatura Notu

    private static final int LAST_COL = COL_NOTE;

    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;
    private final CardRepository cardRepository;
    private final PeriodRepository periodRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;

    public ExcelImportService(ProviderRepository providerRepository,
                              ServiceRepository serviceRepository,
                              CardRepository cardRepository,
                              PeriodRepository periodRepository,
                              ExpenseRepository expenseRepository,
                              InvoiceRepository invoiceRepository) {
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.cardRepository = cardRepository;
        this.periodRepository = periodRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Workbook'taki tanınan ay sheet'lerini import eder ve özet döner.
     * Tek transaction; idempotent (aynı satır iki kez eklenmez).
     */
    @Transactional
    public ImportSummary importMonthlySheets(InputStream xlsx) {
        ImportSummary summary = new ImportSummary();
        // DataFormatter POI'de thread-safe değil; eşzamanlı import'larda paylaşılan
        // örnek parsing'i bozabilir. Bu yüzden her çağrı için lokal örnek kullanılır.
        DataFormatter dataFormatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(xlsx)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName() != null ? sheet.getSheetName().trim() : "";
                String periodCode = MONTH_TO_PERIOD.get(sheetName);
                if (periodCode == null) {
                    // Servisler ve tanınmayan sheet'ler atlanır (E2-02).
                    log.debug("Sheet atlandı (ay değil): {}", sheetName);
                    continue;
                }
                ImportSummary.SheetSummary sheetSummary =
                        importSheet(sheet, sheetName, periodCode, dataFormatter);
                summary.addSheet(sheetSummary);
            }
        } catch (IOException e) {
            throw new ExcelImportException("Excel dosyası okunamadı.", e);
        }
        return summary;
    }

    private ImportSummary.SheetSummary importSheet(Sheet sheet, String sheetName, String periodCode,
                                                   DataFormatter dataFormatter) {
        ImportSummary.SheetSummary sheetSummary = new ImportSummary.SheetSummary(sheetName, periodCode);
        Period period = resolveOrCreatePeriod(periodCode);

        boolean informational = false; // bölüm-başlığından sonra true olur

        int lastRow = sheet.getLastRowNum();
        // Row 0 = header; veri row 1'den başlar (0-indexed).
        for (int r = 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);

            RowClass rowClass = classifyRow(row, dataFormatter);
            switch (rowClass) {
                case EMPTY:
                    sheetSummary.incrementSkippedEmpty();
                    continue;
                case TOTAL:
                    // TOPLAM satırı: bölüm sonu. Bilgi-amaçlı moddan çıkmıyoruz çünkü
                    // tek operasyonel TOPLAM'dan SONRA bilgi bölümleri gelir; her bilgi
                    // bölümü kendi başlık satırıyla informational=true'yu zaten set eder.
                    sheetSummary.incrementSkippedTotal();
                    continue;
                case SECTION_HEADER:
                    // "Multinet Yemek Kartı ... Detay" / "Sağlık Sigortası Detay" →
                    // bundan sonraki veri satırları bilgi-amaçlıdır.
                    informational = true;
                    sheetSummary.incrementSkippedEmpty(); // başlık satırı bir veri değil
                    continue;
                case DATA:
                default:
                    break;
            }

            sheetSummary.incrementRowsRead();
            importDataRow(row, r, period, periodCode, informational, sheetSummary, dataFormatter);
        }

        return sheetSummary;
    }

    private void importDataRow(Row row, int rowIndex, Period period, String periodCode,
                               boolean informational, ImportSummary.SheetSummary sheetSummary,
                               DataFormatter dataFormatter) {
        String serviceName = normalize(getString(row, COL_SERVICE, dataFormatter));
        String providerName = normalize(getString(row, COL_PROVIDER, dataFormatter));
        if (serviceName.isEmpty()) {
            // Hizmet adı yoksa servis çözümlenemez → güvenli atla (boş gibi say).
            log.warn("Hizmet adı boş, satır atlandı: sheet={}, row={}", periodCode, rowIndex + 1);
            sheetSummary.incrementSkippedEmpty();
            return;
        }

        LocalDate transactionDate = getDate(row, COL_DATE, dataFormatter);
        BigDecimal amount = getDecimal(row, COL_AMOUNT, dataFormatter);
        Currency currency = parseCurrency(getString(row, COL_CURRENCY, dataFormatter));
        BigDecimal amountTry = getDecimal(row, COL_AMOUNT_TRY, dataFormatter);
        String cardLast4 = parseCardLast4(getString(row, COL_CARD, dataFormatter));
        String purpose = blankToNull(getString(row, COL_PURPOSE, dataFormatter));
        InvoiceStatus status = parseStatus(getString(row, COL_STATUS, dataFormatter));
        String invoiceNote = blankToNull(getString(row, COL_NOTE, dataFormatter));

        String hash = computeRowHash(periodCode, serviceName, currency, amount, amountTry,
                transactionDate, rowIndex);
        if (expenseRepository.existsBySourceRowHash(hash)) {
            log.warn("Duplicate satır atlandı (aynı sourceRowHash): sheet={}, row={}, "
                    + "hizmet='{}', tarih={}, tutar={}, tlKarsiligi={}",
                    periodCode, rowIndex + 1, serviceName, transactionDate, amount, amountTry);
            sheetSummary.incrementSkippedDuplicate();
            return;
        }

        Card card = cardLast4 != null
                ? cardRepository.findByLastFour(cardLast4).orElse(null)
                : null;

        com.ecommint.accounthr.domain.Service service =
                resolveOrCreateService(serviceName, providerName, card, informational);

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCard(card);
        expense.setTransactionDate(transactionDate);
        expense.setAmount(amount);
        expense.setCurrency(currency);
        expense.setAmountTry(amountTry);
        expense.setInformational(informational);
        expense.setPurpose(purpose);
        expense.setSourceRowHash(hash);
        expense = expenseRepository.save(expense);

        // Taslak invoice: durum Fatura Durumu'ndan, note Fatura Notu'ndan.
        boolean isRefund = (amount != null && amount.signum() < 0)
                || serviceName.toLowerCase(java.util.Locale.forLanguageTag("tr")).contains("iade");
        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(service.getProvider());
        invoice.setStatus(status);
        invoice.setNote(invoiceNote);
        invoice.setRefund(isRefund);
        invoice.setCurrency(currency);
        invoice.setAmount(amount);
        invoiceRepository.save(invoice);

        sheetSummary.incrementImported();
    }

    // ---------------------------------------------------------------------------
    // Satır sınıflandırma
    // ---------------------------------------------------------------------------

    private enum RowClass { EMPTY, TOTAL, SECTION_HEADER, DATA }

    private RowClass classifyRow(Row row, DataFormatter dataFormatter) {
        if (row == null || isRowEmpty(row, dataFormatter)) {
            return RowClass.EMPTY;
        }
        // TOPLAM satırı: herhangi bir hücrede "TOPLAM:" geçer (genelde 5. kolon).
        for (int c = 0; c <= LAST_COL; c++) {
            String v = getString(row, c, dataFormatter).trim();
            String upper = v.toUpperCase(java.util.Locale.forLanguageTag("tr"));
            if (upper.contains("TOPLAM:")) {
                return RowClass.TOTAL;
            }
        }
        // Bölüm başlığı: 1. kolon (Tarih) metni Multinet/Sağlık Sigortası ile başlar,
        // gerisi boş. "Gerisi boş" koşulu (isRestEmpty) zorunludur: gerçek veri satırının
        // 1. kolonunda TARİH olur (hizmet adı 1. kolonda değil), bu yüzden veri satırı
        // asla başlık olarak yanlış sınıflandırılmaz.
        String firstCol = getString(row, COL_DATE, dataFormatter).trim();
        if (isSectionHeaderLabel(firstCol) && isRestEmpty(row, COL_DATE, dataFormatter)) {
            return RowClass.SECTION_HEADER;
        }
        return RowClass.DATA;
    }

    private boolean isSectionHeaderLabel(String label) {
        // Tek kaynak: importer (E2-01) ve reconciler (E2-05) AYNI eşleme kuralını kullanır,
        // böylece reconciler importer'la aynı satırları bilgi-amaçlı sayar (sahte MISMATCH
        // riski yok). Bkz. {@link SectionHeaderText}.
        return SectionHeaderText.isSectionHeaderLabel(label);
    }

    private boolean isRowEmpty(Row row, DataFormatter dataFormatter) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c <= LAST_COL; c++) {
            if (!getString(row, c, dataFormatter).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** {@code exceptCol} dışındaki tüm hücreler boş mu? (bölüm başlığı tespiti). */
    private boolean isRestEmpty(Row row, int exceptCol, DataFormatter dataFormatter) {
        for (int c = 0; c <= LAST_COL; c++) {
            if (c == exceptCol) {
                continue;
            }
            if (!getString(row, c, dataFormatter).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------
    // Çözümleyiciler (resolve-or-create)
    // ---------------------------------------------------------------------------

    private Period resolveOrCreatePeriod(String code) {
        return periodRepository.findByCode(code).orElseGet(() -> {
            String[] parts = code.split("-");
            Period p = new Period();
            p.setYear(Integer.parseInt(parts[0]));
            p.setMonth(Integer.parseInt(parts[1]));
            p.setCode(code);
            return periodRepository.save(p);
        });
    }

    private Provider resolveOrCreateProvider(String name) {
        String providerName = name == null || name.isEmpty() ? "(Bilinmeyen)" : name;
        return providerRepository.findByNameIgnoreCase(providerName).orElseGet(() -> {
            Provider p = new Provider();
            p.setName(providerName);
            return providerRepository.save(p);
        });
    }

    /**
     * Servisi (sağlayıcı + normalize isim) ile bulur; yoksa minimal oluşturur.
     * Eşleşme normalize-isim üzerinden büyük/küçük harf duyarsızdır. E2-02 zenginleştirir.
     */
    private com.ecommint.accounthr.domain.Service resolveOrCreateService(
            String serviceName, String providerName, Card card, boolean informational) {
        Provider provider = resolveOrCreateProvider(providerName);
        List<com.ecommint.accounthr.domain.Service> existing =
                serviceRepository.findByProviderId(provider.getId());
        for (com.ecommint.accounthr.domain.Service s : existing) {
            if (normalize(s.getName()).equalsIgnoreCase(serviceName)) {
                return s;
            }
        }
        com.ecommint.accounthr.domain.Service s = new com.ecommint.accounthr.domain.Service();
        s.setName(serviceName);
        s.setProvider(provider);
        s.setDefaultCard(card);
        // Minimal varsayılanlar (NOT NULL kolonlar): frequency + active_state zorunlu.
        s.setFrequency(Frequency.AD_HOC);
        s.setActiveState(ActiveState.UNCERTAIN);
        s.setInformational(informational);
        return serviceRepository.save(s);
    }

    // ---------------------------------------------------------------------------
    // Hücre parser'ları
    // ---------------------------------------------------------------------------

    /** Hücre değerini metin olarak okur (formül/sayı/tarih dahil), trim'lenmemiş. */
    private String getString(Row row, int col, DataFormatter dataFormatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell);
    }

    private LocalDate getDate(Row row, int col, DataFormatter dataFormatter) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        // Düz metin tarih (DD.MM.YYYY) — savunmacı parse.
        String raw = getString(row, col, dataFormatter).trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            String[] parts = raw.split("[./-]");
            if (parts.length == 3) {
                int d = Integer.parseInt(parts[0].trim());
                int m = Integer.parseInt(parts[1].trim());
                int y = Integer.parseInt(parts[2].trim());
                if (y < 100) {
                    y += 2000;
                }
                return LocalDate.of(y, m, d);
            }
        } catch (RuntimeException ignored) {
            log.warn("Tarih parse edilemedi: '{}'", raw);
        }
        return null;
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
        String raw = getString(row, col, dataFormatter).trim();
        if (raw.isEmpty()) {
            return null;
        }
        // Olası TR sayı biçimi (1.234,56) veya düz sayı.
        String cleaned = raw.replace("₺", "").replace("$", "").replace("€", "")
                .replace(" ", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Tutar parse edilemedi: '{}'", raw);
            return null;
        }
    }

    private Currency parseCurrency(String raw) {
        if (raw == null) {
            return Currency.TRY;
        }
        String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "USD" -> Currency.USD;
            case "EUR" -> Currency.EUR;
            case "GBP" -> Currency.GBP;
            case "TL", "TRY", "" -> Currency.TRY;
            default -> Currency.TRY;
        };
    }

    /** "****3800" → "3800". Boş/eksikse null. */
    private String parseCardLast4(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        return digits.length() > 4 ? digits.substring(digits.length() - 4) : digits;
    }

    /**
     * Durum metnini enum'a eşler (E2-04'te {@link StatusText}'e çıkarıldı; tek kaynak).
     * Tanınmayan/boş → varsayılan {@link InvoiceStatus#EXPECTED}.
     */
    private InvoiceStatus parseStatus(String raw) {
        return StatusText.toStatus(raw);
    }

    // ---------------------------------------------------------------------------
    // Yardımcılar
    // ---------------------------------------------------------------------------

    /** Trim + iç boşlukları teke indir. Null → "". */
    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Stabil SHA-256: (period | normalize(service) | currency | amount | amountTry |
     * transactionDate | rowIndex). Null alanlar boş string ile temsil edilir.
     */
    String computeRowHash(String periodCode, String serviceName, Currency currency,
                          BigDecimal amount, BigDecimal amountTry, LocalDate date, int rowIndex) {
        String payload = String.join("|",
                nz(periodCode),
                normalize(serviceName).toLowerCase(java.util.Locale.ROOT),
                currency == null ? "" : currency.name(),
                amount == null ? "" : amount.stripTrailingZeros().toPlainString(),
                amountTry == null ? "" : amountTry.stripTrailingZeros().toPlainString(),
                date == null ? "" : date.toString(),
                Integer.toString(rowIndex));
        return sha256Hex(payload);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her JVM'de mevcuttur; ulaşılmaz.
            throw new ExcelImportException("SHA-256 algoritması yok.", e);
        }
    }
}
