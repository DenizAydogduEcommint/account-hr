package com.ecommint.accounthr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * E3-11 — {@link KdvCalculator} birim testi. Brüt (KDV-dahil) TL + oran → matrah + KDV.
 */
class KdvCalculatorTest {

    /** Brüt 120, %20 → net 100.00, kdv 20.00. */
    @Test
    void grossOneTwentyAtTwentyPercentYieldsHundredAndTwenty() {
        BigDecimal gross = new BigDecimal("120.00");
        BigDecimal rate = new BigDecimal("20.00");
        assertThat(KdvCalculator.net(gross, rate)).isEqualByComparingTo("100.00");
        assertThat(KdvCalculator.kdv(gross, rate)).isEqualByComparingTo("20.00");
    }

    /** Sonuçlar scale 2 (HALF_UP) — 100, %18 → net 84.75, kdv 15.25 (toplam = brüt). */
    @Test
    void resultsAreScaleTwoAndSumToGross() {
        BigDecimal gross = new BigDecimal("100.00");
        BigDecimal rate = new BigDecimal("18.00");
        BigDecimal net = KdvCalculator.net(gross, rate);
        BigDecimal kdv = KdvCalculator.kdv(gross, rate);
        assertThat(net.scale()).isEqualTo(2);
        assertThat(kdv.scale()).isEqualTo(2);
        assertThat(net.add(kdv)).isEqualByComparingTo(gross);
    }

    /** Negatif brüt (iade) → net ve kdv de negatif (özel durum yok). */
    @Test
    void negativeGrossYieldsNegativeNetAndKdv() {
        BigDecimal gross = new BigDecimal("-120.00");
        BigDecimal rate = new BigDecimal("20.00");
        assertThat(KdvCalculator.net(gross, rate)).isEqualByComparingTo("-100.00");
        assertThat(KdvCalculator.kdv(gross, rate)).isEqualByComparingTo("-20.00");
    }
}
