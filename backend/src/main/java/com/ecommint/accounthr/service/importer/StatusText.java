package com.ecommint.accounthr.service.importer;

import java.util.Locale;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E2-04 — {@code Fatura Durumu} METNİ → {@link InvoiceStatus} eşlemesi (TEK KAYNAK).
 *
 * <p>E2-01 import'unda kullanılan metin eşleme mantığı buraya çıkarıldı, böylece
 * hem {@link ExcelImportService} (satır okuma) hem {@link StatusAuditService}
 * (metin-renk tutarlılık denetimi) AYNI kuralı kullanır. Metin önceliklidir; renk
 * yalnızca doğrulama amaçlıdır (bkz. {@link StatusColors}).
 */
public final class StatusText {

    private StatusText() {
    }

    /**
     * Durum metnini enum'a eşler. Tanınmayan/boş metin → varsayılan
     * {@link InvoiceStatus#EXPECTED} (Bekleniyor).
     */
    public static InvoiceStatus toStatus(String raw) {
        InvoiceStatus resolved = toStatusOrNull(raw);
        return resolved != null ? resolved : InvoiceStatus.EXPECTED;
    }

    /**
     * Durum metnini enum'a eşler; metin tanınmıyor/boşsa {@code null} döner (denetimde
     * "metin yok, çözülemedi" durumunu ayırt etmek için).
     */
    public static InvoiceStatus toStatusOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (equalsTr(v, "Bekleniyor")) {
            return InvoiceStatus.EXPECTED;
        }
        if (equalsTr(v, "Bulundu")) {
            return InvoiceStatus.FOUND;
        }
        if (equalsTr(v, "e-Fatura") || equalsTr(v, "eFatura")) {
            return InvoiceStatus.E_INVOICE;
        }
        if (equalsTr(v, "Araştırılacak")) {
            return InvoiceStatus.TO_INVESTIGATE;
        }
        if (equalsTr(v, "Ignored")) {
            return InvoiceStatus.IGNORED;
        }
        return null;
    }

    private static boolean equalsTr(String a, String b) {
        return a.toLowerCase(Locale.forLanguageTag("tr"))
                .equals(b.toLowerCase(Locale.forLanguageTag("tr")));
    }
}
