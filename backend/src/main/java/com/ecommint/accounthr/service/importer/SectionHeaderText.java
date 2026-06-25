package com.ecommint.accounthr.service.importer;

import java.util.Locale;

/**
 * Bilgi-amaçlı bölüm başlığı ({@code Multinet Yemek Kartı...} / {@code Sağlık Sigortası...})
 * metin tespiti — TEK KAYNAK.
 *
 * <p>E2-01 importer'ı ({@link ExcelImportService}) bu başlıktan SONRA gelen satırları
 * {@code informational=true} olarak yazar; E2-05 mutabakatı ({@link ReconciliationService})
 * AYNI yapısal parse'ı tekrar üretip aynı satırları ana sayımdan hariç tutmalıdır. İki taraf
 * FARKLI eşleme kuralı kullanırsa reconciler, importer'ın yazdığından farklı satır sayar ve
 * SAHTE bir MISMATCH üretir. Bu yüzden eşleme tek bir yerde tutulur.
 *
 * <p>Türkçe-locale uppercase + {@code contains} kullanılır; böylece kullanıcı düzenlemesinden
 * gelen küçük harf veya ASCII (i/İ) varyantları da yakalanır. {@code TRY}'nin "Multinet"
 * gibi geçtiği gerçek hizmet adlarına karşı koruma çağrı tarafındaki {@code isRestEmpty}
 * (başlık satırının gerisi boş olmalı) koşuluyla sağlanır.
 */
public final class SectionHeaderText {

    private SectionHeaderText() {
    }

    /**
     * Verilen etiket bir bilgi-amaçlı bölüm başlığı mı? ({@code Multinet Yemek Kartı} veya
     * {@code Sağlık Sigortası}; küçük/büyük harf ve i/İ duyarsız).
     */
    public static boolean isSectionHeaderLabel(String label) {
        if (label == null) {
            return false;
        }
        // Türkçe i/İ "uppercase tuzağı"na düşmemek için: ROOT-lowercase + Türkçe diakritiği
        // ASCII'ye katla. Böylece "Sağlık", "SAĞLIK", "Saglik", "saglik" hepsi "saglik"e iner
        // ve tek bir ASCII needle ile eşleşir.
        String n = foldTr(label.trim());
        boolean multinet = n.contains("multinet");
        boolean saglikSigorta = n.contains("saglik") && n.contains("sigorta");
        return multinet || saglikSigorta;
    }

    /** ROOT-lowercase + Türkçe diakritiği ASCII'ye katlayan kanonik biçim. */
    private static String foldTr(String s) {
        // ÖNEMLİ: Türkçe i varyantlarını ({@code İ U+0130, I, ı}) lowercase'ten ÖNCE düz
        // 'i'ye katla. Aksi halde {@code İ.toLowerCase(Locale.ROOT)} → "i̇" (i + birleşik
        // nokta) iki karaktere genişler ve birleşik nokta ASCII needle eşleşmesini bozardı
        // (ör. "SİGORTA" → "si̇gorta" → contains("sigorta")=false).
        String prefolded = s.replace('İ', 'i').replace('I', 'i').replace('ı', 'i');
        String lower = prefolded.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case 'ğ' -> sb.append('g');
                case 'ş' -> sb.append('s');
                case 'ç' -> sb.append('c');
                case 'ö' -> sb.append('o');
                case 'ü' -> sb.append('u');
                // 'i' ve düz ASCII default ile geçer.
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
