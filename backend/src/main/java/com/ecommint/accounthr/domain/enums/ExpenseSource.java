package com.ecommint.accounthr.domain.enums;

/**
 * Bir {@link com.ecommint.accounthr.domain.Expense} satırının KAYNAĞI.
 *
 * <ul>
 *   <li>{@code STATEMENT} — banka ekstresi/Excel ay-sheet importer'ından (E2-01) gelen satır.
 *       Mevcut tüm satırlar bu değere migrate edilir (V12 varsayılanı).</li>
 *   <li>{@code MANUAL} — ekstreden ÖNCE veya ekstre olmadan, bir kullanıcının elle girdiği
 *       satır (E3-06). Varsayılan durumu "Bekleniyor" (EXPECTED) olan bir taslak invoice ile
 *       oluşturulur; böylece ileride ekstre gelince ayırt edilebilir.</li>
 * </ul>
 */
public enum ExpenseSource {
    STATEMENT,
    MANUAL
}
