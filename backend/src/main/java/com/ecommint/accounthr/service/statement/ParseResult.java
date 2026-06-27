package com.ecommint.accounthr.service.statement;

import java.util.List;

/**
 * Bir ekstre dosyasının parse çıktısı (E4-01): çıkarılan işlemler + insan-okur uyarılar.
 *
 * <p>Placeholder bank logic için: {@code transactions} BOŞ döner ve {@code warnings}
 * "satır çıkarma henüz tanımlı değil" uyarısını içerir — akış (upload→preview→confirm)
 * yine de uçtan uca çalışır.
 */
public record ParseResult(
        List<ParsedTxn> transactions,
        List<String> warnings) {

    public static ParseResult empty(String warning) {
        return new ParseResult(List.of(), List.of(warning));
    }
}
