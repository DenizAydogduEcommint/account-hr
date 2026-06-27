package com.ecommint.accounthr.domain.enums;

/**
 * Ham işlem (kart ekstresinden parse edilmiş ama HENÜZ servis/expense'e eşleşmemiş)
 * yaşam döngüsü durumu (E4-01).
 *
 * <ul>
 *   <li>{@code PENDING} — parse edildi, önizleme aşamasında; henüz onaylanmadı.</li>
 *   <li>{@code CONFIRMED} — kullanıcı önizlemeyi onayladı; E4-02 eşleştirmesine hazır.</li>
 *   <li>{@code DISCARDED} — kullanıcı bu batch'i reddetti (silinmez, işaretlenir).</li>
 * </ul>
 */
public enum RawTxnStatus {
    PENDING,
    CONFIRMED,
    DISCARDED
}
