package com.ecommint.accounthr.service.drive;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ecommint.accounthr.config.DriveSyncProperties;
import com.ecommint.accounthr.config.StorageProperties;

/**
 * E2-06 — PUSH YASAĞININ yapısal (structural) kanıtı.
 *
 * <p>{@code CLAUDE.md}: "Drive'a lokal dosya push edilmez". Bu test, servisin Drive'a
 * yükleme yapan HİÇBİR public metot AÇMADIĞINI doğrular: yalnızca {@code pullWaiting}
 * (remote→local) ve {@code deleteFromWaiting} (remote silme) vardır. Ayrıca üretilen
 * rclone argümanlarının hiçbirinde lokal→remote yönlü bir kopya (kaynak lokal yol,
 * hedef {@code remote:}) bulunmadığını teyit eder.
 */
class DriveSyncServicePushProhibitionTest {

    @TempDir
    Path localWaiting;

    private DriveSyncService service(FakeCommandRunner runner) {
        DriveSyncProperties p = new DriveSyncProperties();
        p.setEnabled(true);
        p.setRemoteName("gdrive-ecommint");
        p.setWaitingRemotePath("faturalar/waiting");
        p.setRcloneBinary("rclone");
        p.setLocalWaitingDir(localWaiting.toString());
        StorageProperties s = new StorageProperties();
        s.setRoot(localWaiting.getParent().toString());
        return new DriveSyncService(p, s, runner);
    }

    @Test
    void serviceExposesOnlyPullAndDeleteAsDriveOperations() {
        // Bu sınıfta TANIMLANAN (Object'ten miras değil) public metotlar.
        List<String> publicMethods = Arrays.stream(DriveSyncService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .toList();

        // Yalnızca pull + delete dışa açık; upload/push/copyToRemote gibi bir şey YOK.
        assertThat(publicMethods).containsExactlyInAnyOrder("pullWaiting", "deleteFromWaiting");

        // İsim bazlı güvenlik ağı: yükleme çağrıştıran hiçbir metot olmamalı.
        List<String> allMethodNames = Arrays.stream(DriveSyncService.class.getDeclaredMethods())
                .map(Method::getName)
                .map(String::toLowerCase)
                .toList();
        assertThat(allMethodNames).noneMatch(n ->
                n.contains("push") || n.contains("upload")
                        || n.contains("sync") && n.contains("up") || n.contains("toremote"));
    }

    @Test
    void rcloneArgsNeverUploadLocalToRemote() {
        // Pull: kaynak remote:, hedef lokal. Delete: lokal yol hiç geçmez.
        DriveSyncService svc = service(FakeCommandRunner.returning(new CommandResult(0, "", "")));

        List<String> pullArgs = svc.buildPullArgs(localWaiting);
        List<String> deleteArgs = svc.buildDeleteArgs("aws.pdf");

        // copy argümanlarında: ilk yol-arg remote: olmalı (kaynak), lokal yol HEDEF olmalı.
        // remote→local: copy <remote:...> <localDir>
        assertThat(pullArgs.get(1)).isEqualTo("copy");
        assertThat(pullArgs.get(2)).startsWith("gdrive-ecommint:"); // kaynak = remote
        assertThat(pullArgs.get(3)).isEqualTo(localWaiting.toString()); // hedef = lokal
        // Lokal yolun remote'tan ÖNCE (kaynak konumunda) gelmediğini doğrula → push değil.
        assertThat(pullArgs.indexOf("gdrive-ecommint:faturalar/waiting"))
                .isLessThan(pullArgs.indexOf(localWaiting.toString()));

        // delete: yalnızca remote: yol; lokal dizin argümanlarda HİÇ geçmemeli.
        assertThat(deleteArgs).noneMatch(a -> a.equals(localWaiting.toString()));
        assertThat(deleteArgs.get(1)).isEqualTo("deletefile");
        assertThat(deleteArgs.get(2)).startsWith("gdrive-ecommint:");

        // Hiçbir komut "sync"/"copyto" ile remote'a yazma yönünde değil.
        assertThat(pullArgs).doesNotContain("sync");
        assertThat(deleteArgs).doesNotContain("sync", "copy", "copyto");
    }
}
