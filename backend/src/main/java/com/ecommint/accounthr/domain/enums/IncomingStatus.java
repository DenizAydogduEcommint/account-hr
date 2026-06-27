package com.ecommint.accounthr.domain.enums;

/**
 * Ham/gelen faturanın (incoming invoice) yaşam döngüsü durumu (E5-02).
 *
 * <ul>
 *   <li>{@code NEW} — toplandı (pull edildi) ama henüz bir ekstre satırına/expense'e eşleşmedi.</li>
 *   <li>{@code MATCHED} — (E5-04) bir satıra eşleştirildi.</li>
 *   <li>{@code IGNORED} — kullanıcı bu ham faturayı ilgisiz/gereksiz işaretledi.</li>
 * </ul>
 */
public enum IncomingStatus {
    NEW,
    MATCHED,
    IGNORED
}
