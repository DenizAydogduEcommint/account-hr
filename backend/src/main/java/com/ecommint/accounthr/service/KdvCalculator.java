package com.ecommint.accounthr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * E3-11 — KDV (VAT) kırılım hesabı. Brüt (KDV-dahil) TL tutar ve bir oran (yüzde)
 * verildiğinde matrah (net taban) ve KDV tutarını türetir.
 *
 * <pre>
 *   net = gross / (1 + rate/100)
 *   kdv = gross - net
 * </pre>
 *
 * Her iki sonuç da scale 2, {@link RoundingMode#HALF_UP} ile yuvarlanır. Brüt
 * negatifse (iade/refund) net ve kdv de negatif çıkar — bu DOĞRUDUR, özel durum yoktur.
 */
public final class KdvCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private KdvCalculator() {
    }

    /** Matrah (net taban) TL = {@code gross / (1 + rate/100)}, scale 2 HALF_UP. */
    public static BigDecimal net(BigDecimal grossTry, BigDecimal ratePercent) {
        BigDecimal divisor = BigDecimal.ONE.add(ratePercent.divide(HUNDRED));
        // İki ondalık nihai ölçek; bölme için yeterli ara hassasiyet HALF_UP ile.
        return grossTry.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    /** KDV tutarı TL = {@code gross - net}, scale 2 HALF_UP. */
    public static BigDecimal kdv(BigDecimal grossTry, BigDecimal ratePercent) {
        return grossTry.subtract(net(grossTry, ratePercent)).setScale(2, RoundingMode.HALF_UP);
    }
}
