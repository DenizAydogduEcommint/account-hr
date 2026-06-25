package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * E2-05 — {@link SectionHeaderText} (importer + reconciler ORTAK bölüm-başlığı eşlemesi)
 * birim testi. İki tarafın aynı kuralı kullandığını ve i/İ + küçük/büyük harf varyantlarını
 * tutarlı yakaladığını doğrular.
 */
class SectionHeaderTextTest {

    @Test
    void matchesMultinetVariants() {
        assertThat(SectionHeaderText.isSectionHeaderLabel("Multinet Yemek Kartı")).isTrue();
        assertThat(SectionHeaderText.isSectionHeaderLabel("multinet yemek kartı")).isTrue();
        assertThat(SectionHeaderText.isSectionHeaderLabel("MULTINET YEMEK KARTI")).isTrue();
        assertThat(SectionHeaderText.isSectionHeaderLabel("  Multinet Yemek Kartı Detay  ")).isTrue();
    }

    @Test
    void matchesSaglikSigortaVariants() {
        assertThat(SectionHeaderText.isSectionHeaderLabel("Sağlık Sigortası")).isTrue();
        assertThat(SectionHeaderText.isSectionHeaderLabel("sağlık sigortası")).isTrue();
        assertThat(SectionHeaderText.isSectionHeaderLabel("Saglik Sigortasi")).isTrue();
        assertThat(SectionHeaderText.isSectionHeaderLabel("Sağlık Sigortası (Allianz)")).isTrue();
    }

    @Test
    void matchesAllCapsWithDottedCapitalI() {
        // U+0130 İ "SİGORTA" — Türkçe klavye tam-büyük-harf formu. toLowerCase(Locale.ROOT)
        // bunu "i̇" (i + birleşik nokta) iki karaktere genişletir; foldTr lowercase'ten ÖNCE
        // İ→i katladığı için birleşik-nokta tuzağı oluşmaz ve eşleşme TRUE kalır.
        assertThat(SectionHeaderText.isSectionHeaderLabel("SAĞLIK SİGORTASI")).isTrue();
        assertThat(SectionHeaderText.isSectionHeaderLabel("MULTİNET YEMEK KARTI")).isTrue();
    }

    @Test
    void rejectsNonHeaders() {
        assertThat(SectionHeaderText.isSectionHeaderLabel(null)).isFalse();
        assertThat(SectionHeaderText.isSectionHeaderLabel("")).isFalse();
        assertThat(SectionHeaderText.isSectionHeaderLabel("Claude AI")).isFalse();
        assertThat(SectionHeaderText.isSectionHeaderLabel("AWS")).isFalse();
        assertThat(SectionHeaderText.isSectionHeaderLabel("TOPLAM:")).isFalse();
        // Sadece "Sağlık" ya da sadece "Sigorta" tek başına başlık değildir.
        assertThat(SectionHeaderText.isSectionHeaderLabel("Sağlık Hizmeti")).isFalse();
        assertThat(SectionHeaderText.isSectionHeaderLabel("Sigorta Acentesi")).isFalse();
    }
}
