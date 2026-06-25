package com.ecommint.accounthr.service.drive;

/**
 * Drive senkron köprüsü (E2-06) sırasında ÇAĞIRAN GİRDİSİNİN geçersiz olduğu durumlar —
 * ör. geçersiz/traversal içeren dosya adı ({@code ../x.pdf}, {@code a/b.pdf}, {@code x:y}).
 *
 * <p>Bu bir DIŞ bağımlılık (rclone/Drive) hatası DEĞİLDİR; bir <b>istek girdisi</b> hatasıdır.
 * Bu yüzden {@link com.ecommint.accounthr.controller.GlobalExceptionHandler} bunu 502 değil
 * <b>400 BAD_REQUEST</b>'e çevirir. {@link DriveSyncException}'ı genişletir ki mevcut
 * {@code catch (DriveSyncException)} yolları davranışsal olarak bozulmasın; Spring en özgül
 * (specific) {@code @ExceptionHandler}'ı seçtiği için 400 eşlemesi 502 eşlemesinden önce gelir.
 */
public class DriveSyncValidationException extends DriveSyncException {

    public DriveSyncValidationException(String message) {
        super(message);
    }
}
