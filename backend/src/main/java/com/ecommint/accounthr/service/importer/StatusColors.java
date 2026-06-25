package com.ecommint.accounthr.service.importer;

import java.util.Locale;
import java.util.Map;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E2-04 — Fatura durumu ↔ hücre dolgu rengi eşleme tablosu (TEK KAYNAK).
 *
 * <p>Excel'deki {@code Fatura Durumu} hücreleri durumu hem metinle hem hücre dolgu
 * rengiyle (solid fill, doğrudan RGB) kodlar. Bu sınıf renk→durum ve durum→renk
 * sabit eşlemesini tutar. Renk kodları {@code CLAUDE.md} ile bire bir aynıdır ve
 * frontend status badge renkleri de buradan beslenir (tek kaynak ilkesi).
 *
 * <table border="1">
 *   <caption>Renk → Durum eşlemesi</caption>
 *   <tr><th>Hex (RGB)</th><th>Durum</th><th>Metin</th></tr>
 *   <tr><td>{@code 4CAF50}</td><td>{@link InvoiceStatus#FOUND}</td><td>Bulundu</td></tr>
 *   <tr><td>{@code 8BC34A}</td><td>{@link InvoiceStatus#E_INVOICE}</td><td>e-Fatura</td></tr>
 *   <tr><td>{@code FF4444}</td><td>{@link InvoiceStatus#EXPECTED}</td><td>Bekleniyor</td></tr>
 *   <tr><td>{@code FF9800}</td><td>{@link InvoiceStatus#TO_INVESTIGATE} <b>veya</b>
 *       {@link InvoiceStatus#IGNORED}</td><td>Araştırılacak / Ignored</td></tr>
 * </table>
 *
 * <p><b>FF9800 belirsizliği:</b> Araştırılacak ile Ignored AYNI turuncu rengi
 * ({@code FF9800}) kullanır. Bu yüzden renkten yalnızca tek bir kanonik durum
 * ({@link InvoiceStatus#TO_INVESTIGATE}) çözülür ve gerçek ayrım her zaman METİNLE
 * yapılır (bkz. {@link #isAmbiguousColor(String)}). Renk yalnızca doğrulama/yedek
 * amaçlıdır — metin önceliklidir.
 */
public final class StatusColors {

    /** Durum → kanonik hex (RGB, 6 hane, büyük harf). FF9800 iki duruma da işaret eder. */
    public static final Map<InvoiceStatus, String> STATUS_TO_HEX = Map.of(
            InvoiceStatus.FOUND, "4CAF50",
            InvoiceStatus.E_INVOICE, "8BC34A",
            InvoiceStatus.EXPECTED, "FF4444",
            InvoiceStatus.TO_INVESTIGATE, "FF9800",
            InvoiceStatus.IGNORED, "FF9800");

    /**
     * Hex (RGB, 6 hane, büyük harf) → kanonik durum. {@code FF9800} → TO_INVESTIGATE
     * (kanonik); Ignored ayrımı renk değil metinle yapılır.
     */
    private static final Map<String, InvoiceStatus> HEX_TO_STATUS = Map.of(
            "4CAF50", InvoiceStatus.FOUND,
            "8BC34A", InvoiceStatus.E_INVOICE,
            "FF4444", InvoiceStatus.EXPECTED,
            "FF9800", InvoiceStatus.TO_INVESTIGATE);

    /** Birden fazla duruma karşılık gelen (metinle çözülmesi gereken) renk(ler). */
    private static final String AMBIGUOUS_HEX = "FF9800";

    private StatusColors() {
    }

    /**
     * Hex koddan duruma çözer. Girdi 6 hane RGB ya da 8 hane ARGB
     * ({@code 00RRGGBB}/{@code FFRRGGBB}) olabilir; ARGB ise son 6 hane (RGB) alınır.
     * Tanınmayan/boş renk → {@code null}.
     */
    public static InvoiceStatus fromHex(String hex) {
        String rgb = normalizeToRgb(hex);
        if (rgb == null) {
            return null;
        }
        return HEX_TO_STATUS.get(rgb);
    }

    /**
     * Renk birden fazla duruma karşılık geliyor mu (FF9800 → Araştırılacak/Ignored)?
     * Bu durumda metin-renk çelişkisi sayılMAZ; ayrım metinle yapılır.
     */
    public static boolean isAmbiguousColor(String hex) {
        String rgb = normalizeToRgb(hex);
        return AMBIGUOUS_HEX.equals(rgb);
    }

    /**
     * ARGB/RGB hex'i 6 haneli büyük-harf RGB'ye normalize eder.
     *
     * <p>Apache POI {@code XSSFColor#getARGBHex()} 8 hane ({@code 00RRGGBB} veya
     * {@code FFRRGGBB}) döner; bu metot baştaki alpha'yı atıp SON 6 haneyi (RGB) alır.
     * 6 haneli girdi olduğu gibi büyük harfe çevrilir. Geçersiz/boş → {@code null}.
     */
    public static String normalizeToRgb(String hex) {
        if (hex == null) {
            return null;
        }
        String h = hex.trim().toUpperCase(Locale.ROOT);
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (!h.matches("[0-9A-F]+")) {
            return null;
        }
        if (h.length() == 8) {
            // ARGB → son 6 hane RGB.
            return h.substring(2);
        }
        if (h.length() == 6) {
            return h;
        }
        return null;
    }
}
