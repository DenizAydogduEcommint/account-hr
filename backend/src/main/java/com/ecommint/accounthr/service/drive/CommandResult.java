package com.ecommint.accounthr.service.drive;

/**
 * Bir alt-süreç (subprocess) çalıştırmasının sonucu (E2-06).
 *
 * @param exitCode süreç çıkış kodu (0 = başarı)
 * @param stdout   standart çıktı (UTF-8)
 * @param stderr   standart hata (UTF-8)
 */
public record CommandResult(int exitCode, String stdout, String stderr) {

    /** Çıkış kodu 0 mı? */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
