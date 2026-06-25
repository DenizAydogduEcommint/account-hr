package com.ecommint.accounthr.service.drive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link ProcessBuilderCommandRunner} birim testi — yalnızca exit sonrası DRENAJ BÜTÇESİ
 * mantığı doğrulanır; GERÇEK bir alt-süreç (rclone dahil) ÇALIŞTIRILMAZ, dolayısıyla CI
 * üzerinde tam deterministiktir.
 *
 * <p>Regresyon: süreç {@code waitFor} ile başarıyla EXIT ettiğinde, kalan komut bütçesi 0
 * olsa bile boruların boşalması için {@code get(0, MILLIS)} çağrılırsa beklemeden
 * {@code TimeoutException} atılır → başarılı bir rclone copy/deletefile yanlışlıkla
 * "zaman aşımı" raporlanırdı. {@link ProcessBuilderCommandRunner#drainBudget(long)} bu
 * durumda makul bir TABAN (≥5000 ms) döndürerek hatayı önler.
 */
class ProcessBuilderCommandRunnerTest {

    @Test
    void drainBudgetGivesSaneFloorWhenCommandBudgetExhausted() {
        // Komut bütçeye yakın çalıştı: remaining=0 → drenaj için yine de ≥5000 ms verilmeli.
        assertThat(ProcessBuilderCommandRunner.drainBudget(0L)).isGreaterThanOrEqualTo(5_000L);
    }

    @Test
    void drainBudgetFloorsSmallRemaining() {
        // Çok küçük kalan da tabana yükseltilmeli (anlık I/O drenajını "timeout" sanmamak için).
        assertThat(ProcessBuilderCommandRunner.drainBudget(1L)).isGreaterThanOrEqualTo(5_000L);
        assertThat(ProcessBuilderCommandRunner.drainBudget(4_999L)).isEqualTo(5_000L);
    }

    @Test
    void drainBudgetKeepsLargerRemaining() {
        // Hâlâ bol bütçe varsa onu koru (taban ile gereksiz uzatma yapma).
        assertThat(ProcessBuilderCommandRunner.drainBudget(10_000L)).isEqualTo(10_000L);
    }
}
