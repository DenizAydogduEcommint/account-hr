package com.ecommint.accounthr.domain.enums;

/**
 * Ham/gelen faturanın (incoming invoice) toplandığı kaynak (E5-02).
 *
 * <ul>
 *   <li>{@code DRIVE_WAITING} — Google Drive {@code faturalar/waiting/} dizininden pull edildi.</li>
 *   <li>{@code MAIL} — (ileride) e-posta ekinden alındı; {@code sourceRef} = mail messageId.</li>
 * </ul>
 */
public enum IncomingSource {
    DRIVE_WAITING,
    MAIL
}
