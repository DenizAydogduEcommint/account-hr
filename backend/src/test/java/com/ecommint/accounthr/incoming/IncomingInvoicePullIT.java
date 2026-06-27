package com.ecommint.accounthr.incoming;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.IncomingInvoice;
import com.ecommint.accounthr.domain.enums.IncomingSource;
import com.ecommint.accounthr.domain.enums.IncomingStatus;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.IncomingInvoiceRepository;
import com.ecommint.accounthr.service.incoming.RcloneClient;

/**
 * E5-02 — Drive {@code waiting/} pull + ham fatura ingest integration test.
 *
 * <p>Gerçek rclone CI'da YOKTUR: {@link RcloneClient}, landing dizinine dosya bırakan ve TÜM
 * çağrılarını kaydeden bir FAKE bean ile değiştirilir ({@link FakeRcloneConfig}). Fake yalnızca
 * {@code copyToLocal} sunduğundan (arayüzde push/delete YOK) ve her çağrıyı kaydettiğinden,
 * "Drive'a hiçbir delete/push gönderilmedi" iddiası hem yapısal hem de kayıt-bazlı doğrulanır.
 *
 * <p>Storage kökü {@link TempDir} + {@link DynamicPropertySource} ile izole; {@link AbstractDataCleanupIT}
 * before/after truncate ile herhangi bir surefire sırasında izolasyon sağlar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IncomingInvoicePullIT extends AbstractDataCleanupIT {

    private static final String PASSWORD = "s3cret-pass";

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", () -> storageRoot.toString());
    }

    /**
     * Test seam: gerçek rclone yerine landing dizinine dosya yazan + çağrıları kaydeden fake.
     * Bir sonraki copyToLocal çağrısında dökülecek dosyalar {@link #stage(String, String)} ile
     * önceden kuyruğa konur; her copyToLocal çağrısı {@link #calls} listesine işlenir.
     */
    @TestConfiguration
    static class FakeRcloneConfig {

        @Bean
        @Primary
        RecordingFakeRcloneClient recordingFakeRcloneClient() {
            return new RecordingFakeRcloneClient();
        }
    }

    /** Drop-on-copy + call-recording fake. */
    static class RecordingFakeRcloneClient implements RcloneClient {
        /** Kaydedilen çağrılar: "copyToLocal:<remote>:<localDir>". Yalnızca copyToLocal olabilir. */
        final List<String> calls = new CopyOnWriteArrayList<>();
        /** Bir sonraki copyToLocal'da landing'e yazılacak (göreliYol → içerik) dosyalar. */
        final Map<String, String> toDrop = new java.util.concurrent.ConcurrentHashMap<>();

        void stage(String relativePath, String content) {
            toDrop.put(relativePath, content);
        }

        @Override
        public void copyToLocal(String remotePath, Path localDir) {
            calls.add("copyToLocal:" + remotePath + ":" + localDir);
            try {
                Files.createDirectories(localDir);
                for (Map.Entry<String, String> e : toDrop.entrySet()) {
                    Path target = localDir.resolve(e.getKey());
                    Files.createDirectories(target.getParent() == null ? localDir : target.getParent());
                    Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
                }
            } catch (IOException io) {
                throw new RuntimeException(io);
            }
        }
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private AppUserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IncomingInvoiceRepository incomingRepository;
    @Autowired private RecordingFakeRcloneClient fakeRclone;

    private String accountingEmail;
    private String teamMemberEmail;

    @BeforeEach
    void seed() throws IOException {
        fakeRclone.calls.clear();
        fakeRclone.toDrop.clear();
        // Disk izolasyonu: @TempDir sınıf-kapsamlı kalıcıdır; DB her testte truncate edilse de
        // landing/incoming dosyaları diskte birikir → testler arası sızıntı. Storage kökünü temizle.
        if (Files.isDirectory(storageRoot)) {
            try (var paths = Files.walk(storageRoot)) {
                paths.sorted(java.util.Comparator.reverseOrder())
                        .filter(p -> !p.equals(storageRoot))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // yoksay
                            }
                        });
            }
        }

        accountingEmail = "inc.accounting@e-commint.com";
        AppUser accounting = new AppUser();
        accounting.setEmail(accountingEmail);
        accounting.setFullName("Muhasebe");
        accounting.setRole(UserRole.ACCOUNTING);
        accounting.setActive(true);
        accounting.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(accounting);

        teamMemberEmail = "inc.member@e-commint.com";
        AppUser member = new AppUser();
        member.setEmail(teamMemberEmail);
        member.setFullName("Ekip Üyesi");
        member.setRole(UserRole.TEAM_MEMBER);
        member.setActive(true);
        member.setPasswordHash(passwordEncoder.encode(PASSWORD));
        userRepository.save(member);
    }

    // --- helpers ------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private String token(String email) {
        ResponseEntity<Map> resp = rest.postForEntity(
                "/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) resp.getBody().get("accessToken");
    }

    private HttpHeaders authHeaders(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Map<String, Object>> pull(String email) {
        return rest.exchange("/api/v1/incoming/pull", HttpMethod.POST,
                new HttpEntity<>(authHeaders(email)),
                new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    private ResponseEntity<List<Map<String, Object>>> list(String email, String statusQuery) {
        String url = "/api/v1/incoming" + (statusQuery == null ? "" : "?status=" + statusQuery);
        return rest.exchange(url, HttpMethod.GET,
                new HttpEntity<>(authHeaders(email)),
                new ParameterizedTypeReference<List<Map<String, Object>>>() { });
    }

    // --- tests --------------------------------------------------------------

    /** İki dosya pull → newCount=2, 2 IncomingInvoice (NEW) sha256/sourceRef dolu. */
    @Test
    void pullCreatesTwoIncomingInvoices() {
        fakeRclone.stage("aws_subat.pdf", "AWS February invoice content");
        fakeRclone.stage("claude_subat.pdf", "Claude AI February invoice content");

        ResponseEntity<Map<String, Object>> resp = pull(accountingEmail);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(((Number) body.get("pulledCount")).intValue()).isEqualTo(2);
        assertThat(((Number) body.get("newCount")).intValue()).isEqualTo(2);
        assertThat(((Number) body.get("skippedCount")).intValue()).isEqualTo(0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> created = (List<Map<String, Object>>) body.get("newInvoices");
        assertThat(created).hasSize(2);

        List<IncomingInvoice> rows = incomingRepository.findAll();
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(IncomingStatus.NEW);
            assertThat(r.getSource()).isEqualTo(IncomingSource.DRIVE_WAITING);
            assertThat(r.getSha256()).isNotBlank().hasSize(64);
            assertThat(r.getSourceRef()).isNotBlank();
            assertThat(r.getStoredPath()).isNotBlank();
            assertThat(r.getReceivedAt()).isNotNull();
        });
        assertThat(rows).extracting(IncomingInvoice::getFileName)
                .containsExactlyInAnyOrder("aws_subat.pdf", "claude_subat.pdf");

        // Sadece copyToLocal çağrıldı (push/delete YOK).
        assertThat(fakeRclone.calls).hasSize(1);
        assertThat(fakeRclone.calls).allMatch(c -> c.startsWith("copyToLocal:"));
    }

    /** Aynı dosyalarla re-pull → newCount=0, skipped=2 (idempotency), mükerrer satır yok. */
    @Test
    void rePullSameFilesIsIdempotent() {
        fakeRclone.stage("aws_subat.pdf", "AWS February invoice content");
        fakeRclone.stage("claude_subat.pdf", "Claude AI February invoice content");

        ResponseEntity<Map<String, Object>> first = pull(accountingEmail);
        assertThat(((Number) first.getBody().get("newCount")).intValue()).isEqualTo(2);
        assertThat(incomingRepository.count()).isEqualTo(2);

        // Aynı dosyalar yeniden iner (rclone copy değişmeyenleri yine landing'de bırakır).
        ResponseEntity<Map<String, Object>> second = pull(accountingEmail);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = second.getBody();
        assertThat(((Number) body.get("pulledCount")).intValue()).isEqualTo(2);
        assertThat(((Number) body.get("newCount")).intValue()).isEqualTo(0);
        assertThat(((Number) body.get("skippedCount")).intValue()).isEqualTo(2);

        assertThat(incomingRepository.count()).isEqualTo(2); // mükerrer satır yok

        // Hâlâ yalnızca copyToLocal (iki çağrı, push/delete asla).
        assertThat(fakeRclone.calls).hasSize(2);
        assertThat(fakeRclone.calls).allMatch(c -> c.startsWith("copyToLocal:"));
    }

    /** Re-pull'da ikinci farklı dosya → newCount=1 (yalnızca yeni olan). */
    @Test
    void rePullWithOneNewFileAddsOne() {
        fakeRclone.stage("aws_subat.pdf", "AWS February invoice content");
        pull(accountingEmail);
        assertThat(incomingRepository.count()).isEqualTo(1);

        // İkinci, farklı içerikli dosya eklenir (eski dosya da landing'de durur).
        fakeRclone.stage("contabo_subat.pdf", "Contabo VPS February invoice content");
        ResponseEntity<Map<String, Object>> resp = pull(accountingEmail);
        Map<String, Object> body = resp.getBody();
        assertThat(((Number) body.get("pulledCount")).intValue()).isEqualTo(2);
        assertThat(((Number) body.get("newCount")).intValue()).isEqualTo(1);
        assertThat(((Number) body.get("skippedCount")).intValue()).isEqualTo(1);
        assertThat(incomingRepository.count()).isEqualTo(2);
    }

    /** GET /incoming listeler; status filtresi çalışır. */
    @Test
    void listAndStatusFilter() {
        fakeRclone.stage("aws_subat.pdf", "AWS February invoice content");
        fakeRclone.stage("claude_subat.pdf", "Claude AI February invoice content");
        pull(accountingEmail);

        ResponseEntity<List<Map<String, Object>>> all = list(accountingEmail, null);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).hasSize(2);
        assertThat(all.getBody()).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("NEW"));

        // NEW filtresi → 2; IGNORED filtresi → 0.
        assertThat(list(accountingEmail, "NEW").getBody()).hasSize(2);
        assertThat(list(accountingEmail, "IGNORED").getBody()).isEmpty();

        // Birini ignore et → IGNORED filtresi 1, NEW filtresi 1.
        Long id = incomingRepository.findAll().get(0).getId();
        ResponseEntity<Map<String, Object>> ign = rest.exchange(
                "/api/v1/incoming/" + id + "/ignore", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(accountingEmail)),
                new ParameterizedTypeReference<Map<String, Object>>() { });
        assertThat(ign.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ign.getBody().get("status")).isEqualTo("IGNORED");

        assertThat(list(accountingEmail, "IGNORED").getBody()).hasSize(1);
        assertThat(list(accountingEmail, "NEW").getBody()).hasSize(1);
    }

    /** Ekip üyesi → 403 (toplama muhasebe/yönetici işidir). */
    @Test
    void teamMemberPullReturns403() {
        ResponseEntity<Map<String, Object>> resp = pull(teamMemberEmail);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Yetkisiz rol pull tetikleyemez → rclone HİÇ çağrılmamalı.
        assertThat(fakeRclone.calls).isEmpty();
    }

    /** Kimlik doğrulamasız → 401. */
    @Test
    void unauthenticatedReturns401() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/incoming", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** GET listeyi team-member da çağıramaz (403); pull ile aynı yetki. */
    @Test
    void teamMemberListReturns403() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/incoming", HttpMethod.GET,
                new HttpEntity<>(authHeadersString(teamMemberEmail)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders authHeadersString(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(email));
        return headers;
    }

    /**
     * Fix 3 — Eşzamanlı çift-pull mükerrer yarışı izolasyonu: önceden var olan bir satırla
     * (source, sourceRef) çakışan dosya YALNIZCA atlanır (skipped++); aynı batch'teki diğer
     * yeni dosyalar yine de ingest edilir; yanıt 200 (409 DEĞİL), batch geri ALINMAZ.
     * Önceden var olan satır, yarışı kazanan eşzamanlı pull'u temsil eder.
     */
    @Test
    void duplicateCollisionSkipsOnlyThatFileBatchContinues() {
        // Yarışı "kazanmış" diğer pull'u temsil eden, önceden var olan satır:
        // (DRIVE_WAITING, "aws_subat.pdf") zaten DB'de. sourceRef = landing'e göreli yol = dosya adı.
        IncomingInvoice existing = new IncomingInvoice();
        existing.setSource(IncomingSource.DRIVE_WAITING);
        existing.setSourceRef("aws_subat.pdf");
        existing.setFileName("aws_subat.pdf");
        existing.setStoredPath("incoming/preexisting/aws_subat.pdf");
        existing.setSha256("0".repeat(64)); // sha256 farklı → çakışma (source, sourceRef) üzerinden
        existing.setReceivedAt(java.time.Instant.now());
        existing.setStatus(IncomingStatus.NEW);
        incomingRepository.saveAndFlush(existing);
        assertThat(incomingRepository.count()).isEqualTo(1);

        // Pull batch'i: çakışan dosya + tamamen yeni bir dosya.
        fakeRclone.stage("aws_subat.pdf", "AWS February invoice content");
        fakeRclone.stage("claude_subat.pdf", "Claude AI February invoice content");

        ResponseEntity<Map<String, Object>> resp = pull(accountingEmail);
        // Çakışma TÜM batch'i geri almaz → 200, 409 değil.
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = resp.getBody();
        assertThat(((Number) body.get("pulledCount")).intValue()).isEqualTo(2);
        assertThat(((Number) body.get("newCount")).intValue()).isEqualTo(1);   // yalnızca claude
        assertThat(((Number) body.get("skippedCount")).intValue()).isEqualTo(1); // aws atlandı

        // Önceki satır + yeni claude satırı = 2 (aws için mükerrer satır YOK).
        assertThat(incomingRepository.count()).isEqualTo(2);
        assertThat(incomingRepository.findAll())
                .extracting(IncomingInvoice::getFileName)
                .containsExactlyInAnyOrder("aws_subat.pdf", "claude_subat.pdf");
    }

    /**
     * Fix 4 — Landing dizini pull'lar arasında temizlenir: önce A pull edilir (new=1); sonraki
     * pull'da landing'e YALNIZCA B düşer → pulledCount güncel içeriği yansıtır (A+B=2 DEĞİL),
     * B ingest edilir. Eski bug'da (landing hiç temizlenmediği için) A landing'de kalır ve her
     * pull'da yeniden taranıp pulledCount'u şişirirdi.
     */
    @Test
    void landingClearedBetweenPullsReflectsCurrentContents() {
        // 1. pull: yalnızca A.
        fakeRclone.stage("a.pdf", "content A");
        ResponseEntity<Map<String, Object>> first = pull(accountingEmail);
        assertThat(((Number) first.getBody().get("pulledCount")).intValue()).isEqualTo(1);
        assertThat(((Number) first.getBody().get("newCount")).intValue()).isEqualTo(1);
        assertThat(incomingRepository.count()).isEqualTo(1);

        // 2. pull: Drive waiting artık yalnızca B içeriyor (A kaldırıldı). Fake'in drop setini
        // sıfırla ve yalnızca B koy. Landing temizlendiği için eski A landing'de KALMAMALI →
        // pulledCount yalnızca B'yi (güncel içeriği) yansıtır, A+B=2 DEĞİL.
        fakeRclone.toDrop.clear();
        fakeRclone.stage("b.pdf", "content B");
        ResponseEntity<Map<String, Object>> second = pull(accountingEmail);
        Map<String, Object> body2 = second.getBody();
        assertThat(((Number) body2.get("pulledCount")).intValue()).isEqualTo(1);
        assertThat(((Number) body2.get("newCount")).intValue()).isEqualTo(1);
        assertThat(((Number) body2.get("skippedCount")).intValue()).isEqualTo(0);
        assertThat(incomingRepository.count()).isEqualTo(2);
    }

    /**
     * Fix 4 — İçeriği değişmiş bir dosya FARKLI bir sourceRef (Drive yolu) altında geldiğinde,
     * landing temizlendiği için yeni içerik taranır ve sha256 farklı olduğundan içerik-bazlı
     * yeniden ingest edilir (eski bug'da stale landing kopyası eski içerikle taranıp sonsuza
     * dek atlanabilirdi). Aynı içerik (gerçek mükerrer) ise sha256 ile yine elenir.
     */
    @Test
    void replacedContentReingestsByContentTrueDuplicateStillDeduped() {
        fakeRclone.stage("waiting/b.pdf", "content B v1");
        ResponseEntity<Map<String, Object>> first = pull(accountingEmail);
        assertThat(((Number) first.getBody().get("newCount")).intValue()).isEqualTo(1);
        assertThat(incomingRepository.count()).isEqualTo(1);

        // Aynı içerik, farklı yol → gerçek mükerrer (sha256) → atlanır.
        fakeRclone.toDrop.clear();
        fakeRclone.stage("archive/b.pdf", "content B v1");
        ResponseEntity<Map<String, Object>> dup = pull(accountingEmail);
        assertThat(((Number) dup.getBody().get("skippedCount")).intValue()).isEqualTo(1);
        assertThat(((Number) dup.getBody().get("newCount")).intValue()).isEqualTo(0);
        assertThat(incomingRepository.count()).isEqualTo(1);

        // Değişmiş içerik, yeni yol → farklı sha256 → içerik-bazlı yeniden ingest.
        fakeRclone.toDrop.clear();
        fakeRclone.stage("waiting/b_v2.pdf", "content B v2 REPLACED");
        ResponseEntity<Map<String, Object>> repl = pull(accountingEmail);
        assertThat(((Number) repl.getBody().get("newCount")).intValue()).isEqualTo(1);
        assertThat(((Number) repl.getBody().get("skippedCount")).intValue()).isEqualTo(0);
        assertThat(incomingRepository.count()).isEqualTo(2);
    }

    /** copyToLocal'a geçen remote yolu remote→local PULL biçiminde (asla lokal-kaynak). */
    @Test
    void copyToLocalReceivesRemoteSourceOnly() {
        fakeRclone.stage("aws_subat.pdf", "AWS February invoice content");
        pull(accountingEmail);

        assertThat(fakeRclone.calls).hasSize(1);
        String call = fakeRclone.calls.get(0);
        // "copyToLocal:<remote>:<localDir>" — remote kısmı "<remoteName>:<waitingPath>" içerir.
        assertThat(call).startsWith("copyToLocal:");
        assertThat(call).contains("faturalar/waiting");
        // localDir landing alt-dizini, STORAGE_ROOT altında olmalı (lokal hedef).
        assertThat(call).contains("incoming/_landing");
    }
}
