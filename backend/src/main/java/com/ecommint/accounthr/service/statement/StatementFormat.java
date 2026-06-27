package com.ecommint.accounthr.service.statement;

import java.util.Locale;

/**
 * Ekstre dosya formatı (E4-01) — uzantıdan tespit edilir.
 *
 * <ul>
 *   <li>{@code XLSX} / {@code XLS} → Excel (POI XSSF / HSSF).</li>
 *   <li>{@code DOCX} → Word (POI XWPF). Karar: Word için yalnızca .docx desteklenir.</li>
 *   <li>{@code UNSUPPORTED} → .doc / .pdf / diğer → kapsam dışı (uyarı, hata DEĞİL).</li>
 * </ul>
 */
public enum StatementFormat {
    XLSX,
    XLS,
    DOCX,
    UNSUPPORTED;

    /** Dosya adı uzantısından formatı tespit eder. Bilinmeyen/uzantısız → UNSUPPORTED. */
    public static StatementFormat fromFilename(String filename) {
        if (filename == null) {
            return UNSUPPORTED;
        }
        String name = filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return UNSUPPORTED;
        }
        return switch (name.substring(dot + 1)) {
            case "xlsx" -> XLSX;
            case "xls" -> XLS;
            case "docx" -> DOCX;
            default -> UNSUPPORTED;
        };
    }
}
