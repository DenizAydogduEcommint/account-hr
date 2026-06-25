package com.ecommint.accounthr.service.drive;

/**
 * Drive {@code waiting/} senkron köprüsü (E2-06) sırasında oluşan, kullanıcıya/çağırana
 * dönülebilecek hatalar (Drive erişilemez, rclone başarısız/yok, geçersiz dosya adı vb.).
 *
 * <p>Controller bunu temiz bir 4xx'e çevirir (asla 500 değil). Ham {@code rclone}
 * stderr'i veya stack trace bu istisnanın mesajına KOYULMAZ (sır sızıntısını önlemek
 * için); ayrıntı yalnızca log'a yazılır.
 */
public class DriveSyncException extends RuntimeException {

    public DriveSyncException(String message) {
        super(message);
    }

    public DriveSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
