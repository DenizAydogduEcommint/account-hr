package com.ecommint.accounthr.service.invoiceparse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stripe tarzı (ve benzeri) servis fatura PDF'lerini yapılandırılmış alanlara
 * çözümler (E5-03 BACKEND). Desenler GERÇEK örnek faturalardan türetilmiştir
 * (OpenAI EN/TR, ChatGPT, Kapwing, Lucidchart, OpenRouter, Wondershare).
 *
 * <p>Tasarım ilkesi: <b>asla istisna fırlatma</b>. Metin çıkarılamazsa ya da bir desen
 * eşleşmezse ilgili alan {@code null} bırakılır ve bir uyarı eklenir; çağıran eldeki
 * kadarını alır. Para değerleri scale 2'ye normalize edilir; $ / ₺ / binlik ayraçları
 * temizlenir. Hem nokta (1,234.56) hem virgül (1.234,56) ondalık biçimleri desteklenir.
 *
 * <p>KAPSAM: yalnızca PDF. JPG/görüntü OCR bu görevin DIŞINDADIR — ekip bazı ekstrelerin
 * jpg geldiğini belirtti, ancak tüm mevcut örnekler PDF. (Gelecek iş: image OCR, ör.
 * Tesseract/Tess4J ile.)
 */
@Service
public class InvoicePdfParser {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfParser.class);

    // --- Fatura numarası ----------------------------------------------------
    // "Invoice number E15F9A97-0013", "Fatura numarası D610AAA1-0019",
    // "Invoice # 19501968", "Invoice Number: INV26004000019S"
    private static final Pattern INVOICE_NUMBER = Pattern.compile(
            "(?:Invoice\\s+number|Fatura\\s+numaras[ıi]|Invoice\\s+Number|Invoice\\s*#)"
                    + "\\s*:?\\s*([A-Za-z0-9][A-Za-z0-9\\-]*)",
            Pattern.CASE_INSENSITIVE);

    // --- Tarih etiketleri ---------------------------------------------------
    // İngilizce: "Date of issue October 7, 2025", "Invoice date December 5, 2025",
    //            "Billed On Nov 7, 2025", "Order Date: 2025-12-07"
    private static final Pattern DATE_EN = Pattern.compile(
            "(?:Date\\s+of\\s+issue|Invoice\\s+date|Billed\\s+On|Order\\s+Date)"
                    + "\\s*:?\\s*([A-Za-z]+\\.?\\s+\\d{1,2},\\s*\\d{4}|\\d{4}-\\d{2}-\\d{2})",
            Pattern.CASE_INSENSITIVE);
    // Türkçe: "İşlem tarihi 15 Ekim 2025" (İ/I, ş/s ASCII düşüşlerine de toleranslı).
    private static final Pattern DATE_TR = Pattern.compile(
            "(?:[İIi][şsŞS]lem\\s+tarihi|D[üu]zenlenme\\s+tarihi)"
                    + "\\s*:?\\s*(\\d{1,2}\\s+\\p{L}+\\s+\\d{4})",
            Pattern.CASE_INSENSITIVE);

    // --- Toplam -------------------------------------------------------------
    // "Total $36.00", "Toplam $120,00", "Grand Total (USD): 16.31",
    // "Amount due $36.00 USD", "Vadesi gelen tutar $90,00"
    // NOT: "Total excluding tax" / "Vergi hariç toplam" / "Net Total" HARİÇ tutulmalı.
    // Toplam anahtar kelimesi satır BAŞINA çapalanır (^\s*) — böylece bir satır-içi
    // açıklamada geçen "Total" (ör. ürün adı) ya da indirim öncesi birim fiyat eşleşmez.
    private static final Pattern TOTAL = Pattern.compile(
            "^\\s*(?:Grand\\s+Total|Amount\\s+due|Vadesi\\s+gelen\\s+tutar|Total|Toplam)"
                    + "\\s*(?:\\(USD\\)|\\(EUR\\)|\\(TRY\\))?\\s*:?\\s*"
                    + "(?:\\$|€|₺|TL)?\\s*([\\d.,]+)",
            Pattern.CASE_INSENSITIVE);
    // Kesin ödenecek tutar satırı (definitive payable): "Amount due" / "Vadesi gelen" /
    // "Grand Total". Birden çok çapalı eşleşmede bu satır "Total"/"Subtotal"a tercih edilir.
    private static final Pattern TOTAL_DEFINITIVE = Pattern.compile(
            "^\\s*(?:Grand\\s+Total|Amount\\s+due|Vadesi\\s+gelen\\s+tutar)",
            Pattern.CASE_INSENSITIVE);
    // Toplam araması bu satırları atlamalı (matrah/ara toplam değil, brüt istiyoruz).
    private static final Pattern TOTAL_EXCLUDE_LINE = Pattern.compile(
            "excluding\\s+tax|Vergi\\s+hariç|Net\\s+Total|Subtotal|Ara\\s+toplam|Total\\s+excl",
            Pattern.CASE_INSENSITIVE);

    // --- KDV / VAT ----------------------------------------------------------
    // Gerçek örneklerdeki VAT satırı biçimleri:
    //   "VAT - Turkey (20% on $30.00)"           (tutar BİR SONRAKİ satırda: "$6.00")
    //   "VAT - TURKEY ($120,00 üzerinden %0) $0,00"
    //   "VAT 20.00%: 2.72"                        (Wondershare; tutar ":" sonrası)
    // HARİÇ tutulacak satırlar: "VAT NO: ...", "...(EX VAT)...", "PRICE(EX VAT)".
    private static final Pattern VAT_RATE = Pattern.compile(
            "(\\d{1,2}(?:[.,]\\d+)?)\\s*%",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VAT_RATE_TR = Pattern.compile(
            "%\\s*(\\d{1,2}(?:[.,]\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    // VAT satırının KENDİSİNDEKİ tutar = satır SONUNDAKİ son para değeri.
    // "($30.00) $6.00" -> 6.00 ; "VAT 20.00%: 2.72" -> 2.72 ; "...%0) $0,00" -> 0,00
    // Para-sembollü ($/€/₺) varyant önce denenir; yoksa ":" sonrası çıplak sayı (Wondershare).
    private static final Pattern VAT_AMOUNT_SYMBOL = Pattern.compile(
            "(?:\\$|€|₺)\\s*([\\d.,]+)\\s*$");
    private static final Pattern VAT_AMOUNT_COLON = Pattern.compile(
            ":\\s*([\\d.,]+)\\s*$");
    // Bir sonraki satırda yalnızca bir para değeri (OpenAI "$6.00" tek başına satırda).
    private static final Pattern MONEY_ONLY_LINE = Pattern.compile(
            "^\\s*(?:\\$|€|₺)\\s*([\\d.,]+)\\s*$");
    // VAT etiketi gibi görünen ama KDV tutarı OLMAYAN satırlar (vergi no, başlık vb.).
    private static final Pattern VAT_NON_AMOUNT_LINE = Pattern.compile(
            "VAT\\s*NO|EX\\s*VAT|DESCRIPTION|UNIT\\s+PRICE",
            Pattern.CASE_INSENSITIVE);

    // --- Para birimi --------------------------------------------------------
    private static final Pattern CURRENCY_EXPLICIT = Pattern.compile(
            "(?:Currency\\s*:?\\s*|Amount\\s+due[^\\n]*?\\s|due\\s[^\\n]*?\\s)(USD|EUR|TRY|GBP)",
            Pattern.CASE_INSENSITIVE);

    // --- Sağlayıcı (faturayı kesen firma) -----------------------------------
    // İlk satırlardan birinde ", Inc" / ", LLC" / "Limited" / "Software Inc" geçen.
    private static final Pattern PROVIDER_LINE = Pattern.compile(
            "^\\s*([^\\n]*?(?:,?\\s*Inc\\.?|,?\\s*LLC|Limited|Software\\s+Inc\\.?))\\s*$");

    /** Türkçe ay adı -> ay numarası. */
    private static final Map<String, Integer> TR_MONTHS = Map.ofEntries(
            Map.entry("ocak", 1), Map.entry("şubat", 2), Map.entry("subat", 2),
            Map.entry("mart", 3), Map.entry("nisan", 4), Map.entry("mayıs", 5),
            Map.entry("mayis", 5), Map.entry("haziran", 6), Map.entry("temmuz", 7),
            Map.entry("ağustos", 8), Map.entry("agustos", 8), Map.entry("eylül", 9),
            Map.entry("eylul", 9), Map.entry("ekim", 10), Map.entry("kasım", 11),
            Map.entry("kasim", 11), Map.entry("aralık", 12), Map.entry("aralik", 12));

    private static final DateTimeFormatter EN_FULL = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d, yyyy")
            .toFormatter(Locale.ENGLISH);
    private static final DateTimeFormatter EN_ABBR = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d, yyyy")
            .toFormatter(Locale.ENGLISH);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Bir PDF'i çözümler. Asla istisna fırlatmaz — bozuk/boş girdide bile sadece
     * uyarılarla bir {@link ParsedInvoice} döner.
     *
     * @param pdf ham PDF byte'ları (null veya boş olabilir)
     * @return çözümlenmiş alanlar + uyarılar (eldeki kadarı)
     */
    public ParsedInvoice parse(byte[] pdf) {
        List<String> warnings = new ArrayList<>();

        if (pdf == null || pdf.length == 0) {
            warnings.add("PDF içeriği boş.");
            return empty(null, warnings);
        }

        String text;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(doc);
        } catch (Exception e) {
            log.warn("PDF metni çıkarılamadı: {}", e.getMessage());
            warnings.add("PDF okunamadı veya geçerli bir PDF değil: " + e.getMessage());
            return empty(null, warnings);
        }

        if (text == null || text.isBlank()) {
            warnings.add("PDF'ten metin çıkarılamadı (taranmış/görsel olabilir; OCR kapsam dışı).");
            return empty(text, warnings);
        }

        String invoiceNumber = match(INVOICE_NUMBER, text);
        if (invoiceNumber == null) {
            warnings.add("Fatura numarası bulunamadı.");
        }

        LocalDate issueDate = parseDate(text, warnings);

        BigDecimal totalAmount = parseTotal(text, warnings);

        String currency = parseCurrency(text, warnings);

        BigDecimal vatRate = parseVatRate(text);
        BigDecimal vatAmount = parseVatAmount(text);
        if (vatAmount == null && vatRate == null) {
            warnings.add("KDV/VAT satırı bulunamadı (KDV'siz fatura olabilir).");
        }

        String providerName = parseProvider(text);
        if (providerName == null) {
            warnings.add("Sağlayıcı adı bulunamadı.");
        }

        return new ParsedInvoice(invoiceNumber, issueDate, totalAmount, currency,
                vatAmount, vatRate, providerName, text, warnings);
    }

    // --- Tarih --------------------------------------------------------------

    private LocalDate parseDate(String text, List<String> warnings) {
        Matcher en = DATE_EN.matcher(text);
        if (en.find()) {
            LocalDate d = parseEnglishOrIso(en.group(1).trim());
            if (d != null) {
                return d;
            }
        }
        Matcher tr = DATE_TR.matcher(text);
        if (tr.find()) {
            LocalDate d = parseTurkish(tr.group(1).trim());
            if (d != null) {
                return d;
            }
        }
        warnings.add("Düzenlenme tarihi bulunamadı veya çözümlenemedi.");
        return null;
    }

    private LocalDate parseEnglishOrIso(String raw) {
        String s = raw.replace(".", " ").replaceAll("\\s+", " ").trim();
        // ISO: 2025-12-07
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                return LocalDate.parse(s, ISO);
            } catch (Exception ignored) {
                return null;
            }
        }
        try {
            return LocalDate.parse(s, EN_FULL);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(s, EN_ABBR);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate parseTurkish(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        try {
            int day = Integer.parseInt(parts[0]);
            Integer month = TR_MONTHS.get(parts[1].toLowerCase(new Locale("tr", "TR")));
            if (month == null) {
                month = TR_MONTHS.get(parts[1].toLowerCase(Locale.ENGLISH));
            }
            int year = Integer.parseInt(parts[2]);
            if (month == null) {
                return null;
            }
            return LocalDate.of(year, month, day);
        } catch (Exception ignored) {
            return null;
        }
    }

    // --- Toplam -------------------------------------------------------------

    private BigDecimal parseTotal(String text, List<String> warnings) {
        // Satır satır gez. Toplam anahtar kelimesi satır BAŞINA çapalı (^\s*) olmalı; böylece
        // ürün açıklamasında geçen "Total" ya da indirim öncesi birim fiyat YANLIŞLIKLA
        // toplam sayılmaz. Matrah/ara-toplam satırları (TOTAL_EXCLUDE_LINE) atlanır.
        //
        // Seçim mantığı:
        //   1. KESİN ödenecek satır ("Amount due"/"Vadesi gelen"/"Grand Total") varsa, en
        //      SONUNCUSU (sayfadaki nihai ödenecek tutar) tercih edilir.
        //   2. Ancak bu kesin tutar 0 ise (ör. "Amount Due $0.00" — fatura zaten ödenmiş),
        //      gerçek faturalanan tutarı kaybetmemek için "Total" satırlarının en yüksek
        //      pozitif değerine geri düşülür.
        //   3. Hiç kesin satır yoksa, tüm çapalı toplam eşleşmelerinin en yükseği alınır
        //      (VAT dahil brüt toplam matrahtan büyük/eşittir).
        BigDecimal maxAny = null;
        BigDecimal lastDefinitive = null;
        for (String line : text.split("\\R")) {
            if (TOTAL_EXCLUDE_LINE.matcher(line).find()) {
                continue;
            }
            Matcher m = TOTAL.matcher(line);
            if (m.find()) {
                BigDecimal val = parseMoney(m.group(1));
                if (val == null) {
                    continue;
                }
                if (maxAny == null || val.compareTo(maxAny) > 0) {
                    maxAny = val;
                }
                if (TOTAL_DEFINITIVE.matcher(line).find()) {
                    lastDefinitive = val;
                }
            }
        }
        BigDecimal best;
        if (lastDefinitive != null && lastDefinitive.signum() > 0) {
            best = lastDefinitive;
        } else {
            best = maxAny;
        }
        if (best == null) {
            warnings.add("Toplam tutar bulunamadı.");
        }
        return best;
    }

    // --- Para birimi --------------------------------------------------------

    private String parseCurrency(String text, List<String> warnings) {
        Matcher m = CURRENCY_EXPLICIT.matcher(text);
        if (m.find()) {
            return normalizeCurrency(m.group(1));
        }
        if (text.contains("$")) {
            return "USD";
        }
        if (text.contains("€")) {
            return "EUR";
        }
        if (text.contains("₺") || text.contains("TL")) {
            return "TRY";
        }
        warnings.add("Para birimi bulunamadı.");
        return null;
    }

    private String normalizeCurrency(String raw) {
        String c = raw.toUpperCase(Locale.ENGLISH);
        return switch (c) {
            case "USD", "EUR", "TRY", "GBP" -> c;
            default -> c;
        };
    }

    // --- KDV ----------------------------------------------------------------

    private BigDecimal parseVatRate(String text) {
        for (String line : vatAmountLines(text)) {
            Matcher m = VAT_RATE.matcher(line);
            if (m.find()) {
                return parsePlainNumber(m.group(1));
            }
            Matcher tr = VAT_RATE_TR.matcher(line);
            if (tr.find()) {
                return parsePlainNumber(tr.group(1));
            }
        }
        return null;
    }

    private BigDecimal parseVatAmount(String text) {
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!isVatAmountLine(line)) {
                continue;
            }
            // 1) Tutar VAT satırının kendisindeyse: önce para-sembollü ($6.00 / $0,00),
            //    sonra ":" sonrası çıplak sayı (Wondershare "VAT 20.00%: 2.72").
            Matcher sym = VAT_AMOUNT_SYMBOL.matcher(line);
            if (sym.find()) {
                return parseMoney(sym.group(1));
            }
            Matcher colon = VAT_AMOUNT_COLON.matcher(line);
            if (colon.find()) {
                return parseMoney(colon.group(1));
            }
            // 2) Aksi halde OpenAI biçimi: tutar bir SONRAKİ tek-para satırında ("$6.00").
            for (int j = i + 1; j < Math.min(lines.length, i + 3); j++) {
                Matcher only = MONEY_ONLY_LINE.matcher(lines[j].trim());
                if (only.find()) {
                    return parseMoney(only.group(1));
                }
            }
        }
        return null;
    }

    /** VAT içeren ama vergi-no/başlık OLMAYAN (gerçek KDV) satırlar. */
    private List<String> vatAmountLines(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (isVatAmountLine(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private boolean isVatAmountLine(String line) {
        if (!line.toUpperCase(Locale.ENGLISH).contains("VAT")) {
            return false;
        }
        return !VAT_NON_AMOUNT_LINE.matcher(line).find();
    }

    // --- Sağlayıcı ----------------------------------------------------------

    private String parseProvider(String text) {
        String[] lines = text.split("\\R");
        // İlk 25 satırda dur (alt bilgi/şartlarda eşleşmeyi önlemek için).
        int limit = Math.min(lines.length, 25);
        for (int i = 0; i < limit; i++) {
            String line = lines[i];
            Matcher m = PROVIDER_LINE.matcher(line);
            if (m.find()) {
                String candidate = m.group(1).trim();
                // "Bill to" / "Ship to" / "TR TIN" gibi başlıkları ele.
                if (candidate.length() > 2 && !candidate.toLowerCase(Locale.ENGLISH).startsWith("bill")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // --- Para/sayı ayrıştırma -----------------------------------------------

    /**
     * Bir para metnini ($, ₺, €, binlik ayraç, "USD" eki temizlenerek) scale 2
     * {@link BigDecimal}'e çevirir. Hem 1,234.56 hem 1.234,56 biçimini destekler.
     */
    BigDecimal parseMoney(String raw) {
        BigDecimal v = parseNumberFlexible(raw);
        return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Yüzde/oran gibi ölçeklenmemiş sayılar için (ör. "20", "20.00"). */
    BigDecimal parsePlainNumber(String raw) {
        return parseNumberFlexible(raw);
    }

    private BigDecimal parseNumberFlexible(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim()
                .replace("$", "").replace("₺", "").replace("€", "")
                .replaceAll("(?i)USD|EUR|TRY|GBP|TL", "")
                .trim();
        if (s.isEmpty()) {
            return null;
        }
        boolean hasComma = s.indexOf(',') >= 0;
        boolean hasDot = s.indexOf('.') >= 0;
        if (hasComma && hasDot) {
            // Son görülen ayraç ondalık ayraçtır.
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
                // 1.234,56 -> nokta=binlik, virgül=ondalık
                s = s.replace(".", "").replace(",", ".");
            } else {
                // 1,234.56 -> virgül=binlik
                s = s.replace(",", "");
            }
        } else if (hasComma) {
            // Yalnızca virgül var, nokta yok. İki olasılık ayırt edilir:
            //   (a) ABD binlik gruplaması "1,234" / "1,234,567" (ondalık YOK) -> virgüller
            //       binliktir; hepsi silinir -> 1234 / 1234567. Aksi halde "1,234" yanlışlıkla
            //       1.234 (~1.23) olur ve sessiz YANLIŞ tutar oluşur (muhasebe kritik).
            //   (b) TR ondalık "120,00" / "1234,56" / "1.234,56" benzeri -> SON virgül ondalık
            //       ayraçtır; ondan öncekiler (varsa) binliktir.
            // Heuristik: tam binlik grup deseni (^\d{1,3}(,\d{3})+$) ise (a), değilse (b).
            if (s.matches("\\d{1,3}(,\\d{3})+")) {
                s = s.replace(",", "");
            } else {
                int lastComma = s.lastIndexOf(',');
                s = s.substring(0, lastComma).replace(",", "")
                        + "." + s.substring(lastComma + 1);
            }
        }
        // Yalnızca nokta varsa olduğu gibi bırak (1234.56).
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static ParsedInvoice empty(String rawText, List<String> warnings) {
        return new ParsedInvoice(null, null, null, null, null, null, null, rawText, warnings);
    }
}
