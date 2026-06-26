package com.ecommint.accounthr.service.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ecommint.accounthr.config.DriveSyncProperties;
import com.ecommint.accounthr.config.StorageProperties;
import com.ecommint.accounthr.dto.drive.DriveSyncResult;

/**
 * E2-06 — {@link DriveSyncService} birim testleri. Sahte (fake) {@link CommandRunner}
 * kullanılır; GERÇEK bir {@code rclone} HİÇBİR testte çalıştırılmaz, Spring context
 * gerekmez.
 */
class DriveSyncServiceTest {

    @TempDir
    Path localWaiting;

    private DriveSyncProperties props(boolean enabled) {
        DriveSyncProperties p = new DriveSyncProperties();
        p.setEnabled(enabled);
        p.setRemoteName("gdrive-ecommint");
        p.setWaitingRemotePath("faturalar/waiting");
        p.setRcloneBinary("rclone");
        p.setLocalWaitingDir(localWaiting.toString());
        p.setTimeoutSeconds(30);
        return p;
    }

    private StorageProperties storage() {
        StorageProperties s = new StorageProperties();
        s.setRoot(localWaiting.getParent().toString());
        return s;
    }

    // ---- pullWaiting ----

    @Test
    void pullBuildsCorrectRcloneCopyArgsAndDetectsNewFiles() throws IOException {
        // rclone "copy"yi taklit eden fake: çalışınca lokal dizine iki yeni dosya yazar.
        FakeCommandRunner runner = new FakeCommandRunner(args -> {
            try {
                Files.writeString(localWaiting.resolve("aws_subat.pdf"), "x");
                Files.writeString(localWaiting.resolve("zoom_subat.pdf"), "y");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new CommandResult(0, "", "");
        });
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        DriveSyncResult result = svc.pullWaiting();

        // Doğru rclone copy argümanları: remote→local PULL.
        assertThat(runner.lastInvocation()).containsExactly(
                "rclone", "copy", "gdrive-ecommint:faturalar/waiting", localWaiting.toString());
        assertThat(result.skipped()).isFalse();
        assertThat(result.pulledCount()).isEqualTo(2);
        assertThat(result.newFiles()).containsExactly("aws_subat.pdf", "zoom_subat.pdf");
    }

    @Test
    void pullOnlyReportsNewlyArrivedFiles() throws IOException {
        // Önceden var olan bir dosya "yeni" sayılmamalı.
        Files.writeString(localWaiting.resolve("eski.pdf"), "old");
        FakeCommandRunner runner = new FakeCommandRunner(args -> {
            try {
                Files.writeString(localWaiting.resolve("yeni.pdf"), "new");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return new CommandResult(0, "", "");
        });
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        DriveSyncResult result = svc.pullWaiting();

        assertThat(result.newFiles()).containsExactly("yeni.pdf");
        assertThat(result.pulledCount()).isEqualTo(1);
    }

    @Test
    void pullDisabledIsNoOpAndRunnerNeverInvoked() {
        FakeCommandRunner runner = FakeCommandRunner.returning(new CommandResult(0, "", ""));
        DriveSyncService svc = new DriveSyncService(props(false), storage(), runner);

        DriveSyncResult result = svc.pullWaiting();

        assertThat(result.skipped()).isTrue();
        assertThat(result.pulledCount()).isZero();
        assertThat(runner.invocations).isEmpty();
    }

    @Test
    void pullNonZeroExitThrowsDriveSyncException() {
        FakeCommandRunner runner = FakeCommandRunner.returning(
                new CommandResult(3, "", "directory not found"));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        assertThatThrownBy(svc::pullWaiting)
                .isInstanceOf(DriveSyncException.class)
                .hasMessageContaining("pull başarısız");
    }

    @Test
    void pullWhenRcloneMissingThrowsDriveSyncExceptionNotRaw() {
        FakeCommandRunner runner = FakeCommandRunner.throwing("binary bulunamadı: rclone");
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        assertThatThrownBy(svc::pullWaiting)
                .isInstanceOf(DriveSyncException.class)
                .hasMessageContaining("rclone çalıştırılamadı");
    }

    // ---- deleteFromWaiting ----

    @Test
    void deleteBuildsCorrectRcloneDeletefileArgs() {
        FakeCommandRunner runner = FakeCommandRunner.returning(new CommandResult(0, "", ""));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        svc.deleteFromWaiting("aws_subat.pdf");

        assertThat(runner.lastInvocation()).containsExactly(
                "rclone", "deletefile", "gdrive-ecommint:faturalar/waiting/aws_subat.pdf");
    }

    @Test
    void deleteMissingFileIsIdempotentNotFatal() {
        // rclone "not found" → idempotent: istisna atılmamalı, runner çağrılmış olmalı.
        FakeCommandRunner runner = FakeCommandRunner.returning(
                new CommandResult(1, "", "Failed to deletefile: object not found"));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        svc.deleteFromWaiting("yok.pdf"); // throw ETMEMELİ
        assertThat(runner.invocations).hasSize(1);
    }

    @Test
    void deleteOtherFailureThrowsDriveSyncException() {
        FakeCommandRunner runner = FakeCommandRunner.returning(
                new CommandResult(5, "", "quota exceeded"));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        assertThatThrownBy(() -> svc.deleteFromWaiting("aws.pdf"))
                .isInstanceOf(DriveSyncException.class)
                .hasMessageContaining("silme başarısız");
    }

    @Test
    void deleteRejectsPathTraversalFileNames() {
        FakeCommandRunner runner = FakeCommandRunner.returning(new CommandResult(0, "", ""));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        assertThatThrownBy(() -> svc.deleteFromWaiting("../escape.pdf"))
                .isInstanceOf(DriveSyncException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("sub/dir.pdf"))
                .isInstanceOf(DriveSyncException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("a\\b.pdf"))
                .isInstanceOf(DriveSyncException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("remote:other.pdf"))
                .isInstanceOf(DriveSyncException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("  "))
                .isInstanceOf(DriveSyncException.class);

        // Hiçbir geçersiz ad rclone'a ulaşmamalı (silme reddedilmeden önce).
        assertThat(runner.invocations).isEmpty();
    }

    @Test
    void deleteRejectsControlCharsAndNonAsciiFileNames() {
        // FIX 10: \n / \r (log injection) ve ASCII-dışı karakterler reddedilir; rclone'a
        // ulaşmadan validation hatası (400 alt sınıfı) atılır.
        FakeCommandRunner runner = FakeCommandRunner.returning(new CommandResult(0, "", ""));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        assertThatThrownBy(() -> svc.deleteFromWaiting("aws\n.pdf"))
                .isInstanceOf(DriveSyncValidationException.class)
                .isInstanceOf(DriveSyncException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("aws\r.pdf"))
                .isInstanceOf(DriveSyncValidationException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("aws\t.pdf"))
                .isInstanceOf(DriveSyncValidationException.class);
        // ASCII-dışı (Türkçe karakter dahil) reddedilir.
        assertThatThrownBy(() -> svc.deleteFromWaiting("faturağ.pdf"))
                .isInstanceOf(DriveSyncValidationException.class);

        // Hiçbiri rclone'a ulaşmamalı.
        assertThat(runner.invocations).isEmpty();

        // Düz yazdırılabilir-ASCII ad geçerlidir (regresyon koruması).
        svc.deleteFromWaiting("aws_subat.pdf");
        assertThat(runner.invocations).hasSize(1);
    }

    @Test
    void deleteDisabledIsNoOpAndRunnerNeverInvoked() {
        FakeCommandRunner runner = FakeCommandRunner.returning(new CommandResult(0, "", ""));
        DriveSyncService svc = new DriveSyncService(props(false), storage(), runner);

        svc.deleteFromWaiting("aws.pdf");
        assertThat(runner.invocations).isEmpty();
    }

    @Test
    void deleteInvalidFileNameThrowsValidationSubclass_notPlainDriveSync() {
        // Geçersiz ad bir ÇAĞIRAN GİRDİSİ hatasıdır → DriveSyncValidationException (handler 400'e
        // çevirir). Düz DriveSyncException (→502) OLMAMALI; alt sınıf olduğu için instanceof
        // DriveSyncException de doğru kalır (mevcut catch yolları bozulmaz).
        FakeCommandRunner runner = FakeCommandRunner.returning(new CommandResult(0, "", ""));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        assertThatThrownBy(() -> svc.deleteFromWaiting("../escape.pdf"))
                .isInstanceOf(DriveSyncValidationException.class)
                .isInstanceOf(DriveSyncException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("a/b.pdf"))
                .isInstanceOf(DriveSyncValidationException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("x:y"))
                .isInstanceOf(DriveSyncValidationException.class);
        assertThatThrownBy(() -> svc.deleteFromWaiting("  "))
                .isInstanceOf(DriveSyncValidationException.class);
        assertThat(runner.invocations).isEmpty();
    }

    @Test
    void deleteRcloneFailureStaysPlainDriveSync_not400Subclass() {
        // Gerçek rclone başarısızlığı (dış bağımlılık) → düz DriveSyncException (→502),
        // validation alt sınıfı DEĞİL.
        FakeCommandRunner runner = FakeCommandRunner.returning(
                new CommandResult(5, "", "quota exceeded"));
        DriveSyncService svc = new DriveSyncService(props(true), storage(), runner);

        assertThatThrownBy(() -> svc.deleteFromWaiting("aws.pdf"))
                .isInstanceOf(DriveSyncException.class)
                .isNotInstanceOf(DriveSyncValidationException.class);
    }

    @Test
    void deleteValidatesFileNameEvenWhenBridgeDisabled() {
        // Doğrulama enabled-check'ten ÖNCE: kapalıyken bile traversal/geçersiz ad reddedilir
        // (doğrulanmamış ad log'a yazılmaz).
        FakeCommandRunner runner = FakeCommandRunner.returning(new CommandResult(0, "", ""));
        DriveSyncService svc = new DriveSyncService(props(false), storage(), runner);

        assertThatThrownBy(() -> svc.deleteFromWaiting("../escape.pdf"))
                .isInstanceOf(DriveSyncException.class);
        assertThat(runner.invocations).isEmpty();
    }
}
