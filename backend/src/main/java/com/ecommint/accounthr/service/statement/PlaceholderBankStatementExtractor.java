package com.ecommint.accounthr.service.statement;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * E4-01 PLACEHOLDER extractor — gerçek banka-özgül satır çıkarımı HENÜZ tanımlı değildir
 * (örnek ekstre bekleniyor). Tüm kart/format kombinasyonlarını "destekler" gibi davranır
 * ama daima BOŞ liste döner; gerçek uyarıyı {@link DefaultStatementParser} ekler.
 *
 * <p>Gerçek bir extractor eklendiğinde: o extractor'ı daha yüksek öncelikle
 * (ör. {@code @Order}) tanımlayın ve {@link #supports} ile kendi banka/kartını seçsin;
 * bu placeholder yalnızca eşleşme olmayan kalan durumlar için fallback kalır.
 *
 * @param <D> POI doküman tipi (kullanılmaz — placeholder)
 */
@Component
public class PlaceholderBankStatementExtractor<D> implements BankStatementExtractor<D> {

    @Override
    public boolean supports(String cardLast4, StatementFormat format) {
        // Placeholder: her şeyi "destekler" (fallback). Gerçek extractor'lar daha özgül seçer.
        return true;
    }

    @Override
    public List<ParsedTxn> extract(D document, String cardLast4, StatementFormat format) {
        // TODO E4-01: gerçek satır çıkarma — örnek ekstre gelince.
        // Burada açılmış POI doküman modelinden (XSSF/HSSF/XWPF) banka-özgül satırlar
        // okunup ParsedTxn listesine dönüştürülecek. Şu an boş liste döner; çağıran
        // (DefaultStatementParser) "parser henüz tanımlı değil" uyarısını ekler.
        return List.of();
    }
}
