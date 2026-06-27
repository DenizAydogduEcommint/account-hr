package com.ecommint.accounthr.service.incoming;

/**
 * rclone pull (copy remote → local) sırasında dış-bağımlılık hatası (E5-02): binary yok,
 * sıfırdan farklı çıkış kodu veya zaman aşımı. Çağıran girdisi hatası DEĞİLDİR; bir dış
 * servis (rclone/Drive) başarısızlığıdır → {@link com.ecommint.accounthr.controller.GlobalExceptionHandler}
 * bunu 502 BAD_GATEWAY'e çevirir.
 */
public class RcloneException extends RuntimeException {

    public RcloneException(String message) {
        super(message);
    }

    public RcloneException(String message, Throwable cause) {
        super(message, cause);
    }
}
