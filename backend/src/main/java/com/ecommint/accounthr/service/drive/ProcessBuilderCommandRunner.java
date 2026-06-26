package com.ecommint.accounthr.service.drive;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * {@link CommandRunner}'ın gerçek (production) implementasyonu — {@link ProcessBuilder}
 * ile alt-süreç çalıştırır (E2-06).
 *
 * <p>stdout ve stderr AYNI ANDA (eşzamanlı, ayrı iş parçacıklarında) okunur; süreç
 * {@code timeout} içinde bitmezse zorla sonlandırılır ve {@link CommandExecutionException}
 * fırlatılır. Binary bulunamazsa ({@link IOException}) yine {@link CommandExecutionException}
 * fırlatılır.
 *
 * <p><b>Neden eşzamanlı okuma?</b> stdout'u tamamen okuyup SONRA stderr'i okumak klasik bir
 * kilitlenmeye (deadlock) yol açar: alt-süreç stderr boru tamponunu (pipe buffer, ~64KB)
 * doldurursa stderr yazarken bloke olur; biz de stdout {@code readAllBytes()}'te bloke
 * kalırız — ikisi de ilerleyemez. rclone hata durumlarında (kota, auth, verbose) büyük
 * stderr üretebilir. Bu yüzden iki akım da paralel drenajla okunur ve toplam okuma
 * {@code timeout} ile sınırlandırılır (okuma fazında da zaman aşımı korur).
 *
 * <p>Testlerde bu sınıf KULLANILMAZ — bunun yerine sahte (fake) bir {@link CommandRunner}
 * enjekte edilir; böylece gerçek {@code rclone} hiçbir testte exec edilmez.
 */
@Component
public class ProcessBuilderCommandRunner implements CommandRunner {

    @Override
    public CommandResult run(List<String> args, Duration timeout) {
        if (args == null || args.isEmpty()) {
            throw new CommandExecutionException("Boş komut çalıştırılamaz.");
        }
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false); // stdout/stderr ayrı kanallar (delete idempotency stderr'e bakar)

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            // En tipik durum: binary PATH'te yok (rclone kurulu değil).
            throw new CommandExecutionException(
                    "Komut başlatılamadı (binary bulunamadı veya çalıştırılamadı): " + args.get(0), e);
        }

        // İki akımı PARALEL oku (kilitlenmeyi önler). Reader'lar daemon thread'lerde çalışır;
        // zaman aşımı/kesintide süreç destroyForcibly ile öldürülür → boru kapanır → readAllBytes()
        // "Stream closed" alır ve thread serbest kalır (cancel(true) native read'i KESEMEZ; gerçek
        // çözücü mekanizma boru kapanmasıdır). Daemon oldukları için JVM çıkışını engellemezler.
        CompletableFuture<String> stdoutF = readAsync(process.getInputStream());
        CompletableFuture<String> stderrF = readAsync(process.getErrorStream());

        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            long waitMs = remainingMillis(deadline);
            boolean finished = process.waitFor(waitMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                throw timedOut(process, stdoutF, stderrF, args.get(0), timeout);
            }
            // Süreç EXIT etti → boruların boşalması (drain) artık SINIRSIZ beklenir. Ölü bir
            // sürecin stdout/stderr boruları SONLUDUR (yazan taraf kapandı) ve mutlaka EOF'a
            // ulaşır; reader'lar readAllBytes()'ı tamamlar. Burada timeout vermek (eski
            // max(remaining,5s)) BÜYÜK bir rclone stderr'inde yanlışlıkla zaman aşımı atıp
            // başarılı kopyala/sil'i 502 olarak raporlardı. allOf(...).join() EOF'a kadar bekler.
            // (Gerçek timeout/kill yolu finished==false'ta yukarıda kalır — değişmedi.)
            CompletableFuture.allOf(stdoutF, stderrF).join();
            return new CommandResult(process.exitValue(), stdoutF.join(), stderrF.join());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            stdoutF.cancel(true);
            stderrF.cancel(true);
            Thread.currentThread().interrupt();
            throw new CommandExecutionException("Komut beklenirken kesinti oldu: " + args.get(0), e);
        } catch (CompletionException e) {
            // allOf(...).join() drenaj sırasında bir reader UncheckedIOException ile patlarsa
            // CompletionException sarmalar (artık get(...) yok → ExecutionException oluşmaz).
            process.destroyForcibly();
            throw new CommandExecutionException("Komut çıktısı okunamadı: " + args.get(0), e);
        }
    }

    /**
     * Süreç EXIT ettikten SONRA boruları boşaltmak (drain) için verilecek bütçe.
     * Kalan komut bütçesi 0/küçük olsa bile en az {@code MIN_DRAIN_MILLIS} verilir:
     * süreç zaten 0 çıktı kodu ile bitti, akımların EOF'a ulaşması yalnızca anlık bir
     * I/O gecikmesidir; bunu "zaman aşımı" saymak başarılı işlemi yanlış raporlardı.
     */
    static long drainBudget(long remainingMillis) {
        return Math.max(remainingMillis, MIN_DRAIN_MILLIS);
    }

    /** Süreç exit sonrası boru drenajı için minimum bekleme tabanı (ms). */
    private static final long MIN_DRAIN_MILLIS = 5_000L;

    /** Verilen son-ana (deadline) kalan süre (en az 0 ms). */
    private static long remainingMillis(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining > 0 ? TimeUnit.NANOSECONDS.toMillis(remaining) : 0L;
    }

    /** Süreci öldür, reader'ları iptal et ve standart zaman aşımı istisnasını üret. */
    private static CommandExecutionException timedOut(Process process, CompletableFuture<?> a,
            CompletableFuture<?> b, String binary, Duration timeout) {
        process.destroyForcibly();
        a.cancel(true);
        b.cancel(true);
        return new CommandExecutionException(
                "Komut zaman aşımına uğradı (" + timeout.toSeconds() + " sn): " + binary);
    }

    /** Bir akımı ayrı (daemon) iş parçacığında tamamen okur; I/O hatasını sarmalayarak iletir. */
    private static CompletableFuture<String> readAsync(InputStream in) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = in) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, command -> {
            Thread t = new Thread(command, "rclone-stream-reader");
            t.setDaemon(true);
            t.start();
        });
    }
}
