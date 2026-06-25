package com.ecommint.accounthr.service.drive;

/**
 * Bir alt-sürecin BAŞLATILAMAMASI (binary yok), zaman aşımı veya kesinti gibi
 * <b>çalıştırma-altyapısı</b> hataları için fırlatılır (E2-06).
 *
 * <p>Dikkat: süreç çalıştı ama sıfırdan farklı çıkış kodu döndürdüyse bu istisna
 * ATILMAZ — onun yerine {@link CommandResult#exitCode()} > 0 döner ve kararı
 * {@link DriveSyncService} verir. Bu istisna "komutu hiç çalıştıramadık/bitiremedik"
 * durumunu temsil eder.
 */
public class CommandExecutionException extends RuntimeException {

    public CommandExecutionException(String message) {
        super(message);
    }

    public CommandExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
