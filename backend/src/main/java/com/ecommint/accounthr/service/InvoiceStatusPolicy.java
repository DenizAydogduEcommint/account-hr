package com.ecommint.accounthr.service;

import org.springframework.stereotype.Component;

import com.ecommint.accounthr.domain.enums.InvoiceStatus;

/**
 * E3-07 — Fatura durumu geçiş politikası (state machine).
 *
 * <p>İzin verilen {@code from → to} geçişlerini tek noktadan tanımlar. PATCH ucu durum
 * değişimini her zaman bu nesnenin {@link #isAllowed(InvoiceStatus, InvoiceStatus)}
 * metodundan geçirir. İleride bir geçişin kısıtlanması GEREKİRSE değişiklik tek satırlık
 * olur (matrise {@code false} ekle); controller/servis akışı sabit kalır.
 *
 * <p><b>MVP KARARI — PERMISSIVE:</b> Açık sorunun (open question) MVP yanıtı olarak şu an
 * TÜM geçişlere izin verilir (audit ile birlikte). Yani bu politika bir gerçek nesne
 * (matris) olarak vardır ama her {@code (from, to)} için {@code true} döner. İş kuralları
 * netleştiğinde (ör. "IGNORED'dan FOUND'a dönülemez") yalnızca {@link #ALLOWED} matrisi
 * güncellenir; her durum değişimi audit_log'a {@code STATUS_CHANGE} olarak düşmeye devam
 * eder (AuditInterceptor üzerinden).
 */
@Component
public class InvoiceStatusPolicy {

    private static final InvoiceStatus[] VALUES = InvoiceStatus.values();

    /**
     * İzin matrisi: {@code ALLOWED[from.ordinal()][to.ordinal()]}. MVP'de tamamı
     * {@code true} (permissive). Bir geçişi kapatmak için ilgili hücreyi {@code false} yap.
     */
    private static final boolean[][] ALLOWED = buildPermissiveMatrix();

    private static boolean[][] buildPermissiveMatrix() {
        int n = VALUES.length;
        boolean[][] matrix = new boolean[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                // MVP: kararla permissive — her geçişe izin (aynı duruma "değişim" dahil).
                matrix[from][to] = true;
            }
        }
        return matrix;
    }

    /**
     * {@code from} durumundan {@code to} durumuna geçişe izin var mı?
     *
     * @param from mevcut durum (null ise — temsilci invoice durumu yok — herhangi bir
     *             hedefe geçişe izin verilir; ilk durum atamasıdır)
     * @param to   hedef durum (zorunlu)
     * @return MVP'de her zaman {@code true}; matris sıkılaştırıldığında ilgili hücre
     */
    public boolean isAllowed(InvoiceStatus from, InvoiceStatus to) {
        if (to == null) {
            return false;
        }
        if (from == null) {
            // İlk durum ataması (temsilci invoice yoktu) — kısıtlanmaz.
            return true;
        }
        return ALLOWED[from.ordinal()][to.ordinal()];
    }
}
