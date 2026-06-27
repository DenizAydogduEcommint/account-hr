package com.ecommint.accounthr.service.statement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * E4-01 — Varsayılan {@link StatementParser}. Akış:
 * <ol>
 *   <li>Uzantıdan formatı tespit et ({@link StatementFormat}).</li>
 *   <li>Desteklenen format ise dokümanı POI ile AÇ (plumbing'in çalıştığını kanıtlar:
 *       .xlsx → XSSFWorkbook, .xls → HSSFWorkbook, .docx → XWPFDocument).</li>
 *   <li>Banka-özgül satır çıkarımını uygun {@link BankStatementExtractor}'a delege et.</li>
 *   <li>.doc / .pdf / bilinmeyen → desteklenmiyor uyarısı (500 DEĞİL).</li>
 * </ol>
 *
 * <p><b>PLACEHOLDER (E4-01):</b> gerçek satır çıkarımı henüz yoktur (örnek ekstre bekleniyor).
 * Doküman başarıyla açılsa bile extractor şu an boş liste döner; bu sınıf bunu net bir
 * uyarıyla ({@link #PLACEHOLDER_WARNING}) raporlar. Böylece upload→preview→confirm akışı
 * uçtan uca çalışır. Gerçek extractor sonradan {@link BankStatementExtractor} arkasına
 * eklenir; bu sınıf değişmez.
 */
@Service
public class DefaultStatementParser implements StatementParser {

    private static final Logger log = LoggerFactory.getLogger(DefaultStatementParser.class);

    /** Banka-özgül satır çıkarımı henüz tanımlı değil (E4-01) — örnek ekstre bekleniyor. */
    public static final String PLACEHOLDER_WARNING =
            "Otomatik satır çıkarma bu format/banka için henüz tanımlı değil — "
                    + "örnek ekstre bekleniyor (E4-01 parser).";

    private final List<BankStatementExtractor<Object>> extractors;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public DefaultStatementParser(List<BankStatementExtractor> extractors) {
        // Spring tüm BankStatementExtractor bean'lerini enjekte eder (şimdilik placeholder).
        this.extractors = new ArrayList<>();
        for (BankStatementExtractor e : extractors) {
            this.extractors.add(e);
        }
    }

    @Override
    public ParseResult parse(byte[] content, String filename, String cardLast4) {
        StatementFormat format = StatementFormat.fromFilename(filename);
        if (format == StatementFormat.UNSUPPORTED) {
            String warning = "Desteklenmeyen dosya formatı: " + safeName(filename)
                    + " — yalnızca .xlsx, .xls, .docx desteklenir (.doc/.pdf kapsam dışı, E4-01).";
            log.info("Statement parse: unsupported format file={} card={}", safeName(filename), cardLast4);
            return ParseResult.empty(warning);
        }

        try {
            List<ParsedTxn> txns = switch (format) {
                case XLSX -> parseXlsx(content, cardLast4, format);
                case XLS -> parseXls(content, cardLast4, format);
                case DOCX -> parseDocx(content, cardLast4, format);
                default -> List.of(); // unreachable (UNSUPPORTED already returned)
            };

            List<String> warnings = new ArrayList<>();
            if (txns.isEmpty()) {
                // Plumbing çalıştı (doküman açıldı) ama extractor henüz gerçek satır çıkarmıyor.
                warnings.add(PLACEHOLDER_WARNING);
            }
            return new ParseResult(txns, warnings);
        } catch (IOException | RuntimeException ex) {
            // Bozuk/okunamayan doküman → 500 DEĞİL; net uyarı.
            log.warn("Statement parse failed file={} card={}: {}",
                    safeName(filename), cardLast4, ex.getMessage());
            return ParseResult.empty("Dosya açılamadı veya bozuk (" + format + "): "
                    + safeName(filename));
        }
    }

    // ------------------------------------------------------------------------
    // Format-özgül açma (POI) + extractor delegasyonu
    // ------------------------------------------------------------------------

    private List<ParsedTxn> parseXlsx(byte[] content, String cardLast4, StatementFormat format)
            throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            return delegate(workbook, cardLast4, format);
        }
    }

    private List<ParsedTxn> parseXls(byte[] content, String cardLast4, StatementFormat format)
            throws IOException {
        try (HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(content))) {
            return delegate(workbook, cardLast4, format);
        }
    }

    private List<ParsedTxn> parseDocx(byte[] content, String cardLast4, StatementFormat format)
            throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return delegate(document, cardLast4, format);
        }
    }

    /** Açılmış POI dokümanını uygun extractor'a yollar (yoksa boş liste). */
    private List<ParsedTxn> delegate(Object document, String cardLast4, StatementFormat format) {
        for (BankStatementExtractor<Object> extractor : extractors) {
            if (extractor.supports(cardLast4, format)) {
                List<ParsedTxn> result = extractor.extract(document, cardLast4, format);
                return result != null ? result : List.of();
            }
        }
        return List.of();
    }

    private static String safeName(String filename) {
        return filename == null ? "(adsız)" : filename;
    }
}
