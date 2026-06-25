package com.ecommint.accounthr.service.drive;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Test sahtesi (fake) — GERÇEK bir {@code rclone} ÇALIŞTIRMAZ (E2-06).
 *
 * <p>Çağrılan her komutun argümanlarını kaydeder ({@link #invocations}) ve
 * yapılandırılabilir bir cevap döndürür. İsteğe bağlı olarak {@link CommandExecutionException}
 * fırlatarak "binary yok / zaman aşımı" durumunu taklit edebilir. Bu sınıf sayesinde
 * hiçbir testte gerçek alt-süreç exec edilmez.
 */
final class FakeCommandRunner implements CommandRunner {

    final List<List<String>> invocations = new ArrayList<>();
    private final Function<List<String>, CommandResult> responder;
    private boolean throwExecution = false;
    private String throwMessage = "binary not found";

    FakeCommandRunner(Function<List<String>, CommandResult> responder) {
        this.responder = responder;
    }

    /** Her çağrıda sabit sonuç döndüren basit fake. */
    static FakeCommandRunner returning(CommandResult result) {
        return new FakeCommandRunner(args -> result);
    }

    /** Çağrıldığında {@link CommandExecutionException} fırlatan fake (rclone yok gibi). */
    static FakeCommandRunner throwing(String message) {
        FakeCommandRunner f = new FakeCommandRunner(args -> {
            throw new IllegalStateException("unreachable");
        });
        f.throwExecution = true;
        f.throwMessage = message;
        return f;
    }

    @Override
    public CommandResult run(List<String> args, Duration timeout) {
        invocations.add(List.copyOf(args));
        if (throwExecution) {
            throw new CommandExecutionException(throwMessage);
        }
        return responder.apply(args);
    }

    List<String> lastInvocation() {
        if (invocations.isEmpty()) {
            return List.of();
        }
        return invocations.get(invocations.size() - 1);
    }
}
