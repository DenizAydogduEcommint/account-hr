package com.ecommint.accounthr.service.statement;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.RawTransaction;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.RawTxnStatus;
import com.ecommint.accounthr.dto.statement.StatementBatchActionResponse;
import com.ecommint.accounthr.dto.statement.StatementPreviewResponse;
import com.ecommint.accounthr.dto.statement.StatementTxnDto;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.RawTransactionRepository;

/**
 * E4-01 — Banka ekstresi yükleme + parse pipeline'ı.
 *
 * <h2>upload (önizleme)</h2>
 * <ol>
 *   <li>Kart (last4) + ay (YYYY-MM) doğrulanır; period find-or-create edilir.</li>
 *   <li>Dosya içeriği okunur, SHA-256 hesaplanır (batchRef + idempotency anahtarı).</li>
 *   <li><b>Idempotency:</b> aynı (sha256 + kart + dönem) için CONFIRMED bir batch zaten
 *       varsa → {@code alreadyUploaded=true} döner, yeniden parse/persist YAPILMAZ.</li>
 *   <li>Aksi halde {@link StatementParser} ile parse edilir; çıkan işlemler (placeholder:
 *       boş) {@code PENDING} {@link RawTransaction} olarak persist edilir.</li>
 * </ol>
 *
 * <p>Dosya da {@link StatementStorageService} ile STORAGE_ROOT altında saklanır (denetim).
 * Eşleştirme/expense üretimi BU servisin işi değildir (E4-02) — satırlar {@code matched=false}.
 */
@Service
public class StatementUploadService {

    private static final Logger log = LoggerFactory.getLogger(StatementUploadService.class);

    private final CardRepository cardRepository;
    private final PeriodRepository periodRepository;
    private final RawTransactionRepository rawTransactionRepository;
    private final StatementParser statementParser;
    private final StatementStorageService statementStorageService;

    public StatementUploadService(CardRepository cardRepository,
                                  PeriodRepository periodRepository,
                                  RawTransactionRepository rawTransactionRepository,
                                  StatementParser statementParser,
                                  StatementStorageService statementStorageService) {
        this.cardRepository = cardRepository;
        this.periodRepository = periodRepository;
        this.rawTransactionRepository = rawTransactionRepository;
        this.statementParser = statementParser;
        this.statementStorageService = statementStorageService;
    }

    /** Önizleme: dosya yükle → parse → PENDING satırlar persist → preview DTO. */
    @Transactional
    public StatementPreviewResponse upload(MultipartFile file, String cardLast4, String month) {
        if (file == null || file.isEmpty()) {
            throw new StatementUploadException("Boş dosya yüklenemez.");
        }
        Card card = resolveCard(cardLast4);
        YearMonth ym = parseMonth(month);
        Period period = resolveOrCreatePeriod(ym);

        byte[] content = readBytes(file);
        String sha256 = sha256(content);
        // Path-traversal koruması: yalnızca dosya adı saklanır (örn. "../../etc/passwd" → "passwd").
        String filename = sanitizeFilename(file.getOriginalFilename());

        // Idempotency: aynı dosya + kart + dönem için CONFIRMED batch zaten varsa tekrar etme.
        boolean alreadyConfirmed = rawTransactionRepository
                .existsBySourceFileSha256AndCardIdAndPeriodIdAndStatus(
                        sha256, card.getId(), period.getId(), RawTxnStatus.CONFIRMED);
        if (alreadyConfirmed) {
            List<RawTransaction> existing = rawTransactionRepository
                    .findBySourceFileSha256AndCardIdAndPeriodId(sha256, card.getId(), period.getId());
            log.info("Statement upload idempotent hit: sha256={} card={} month={} ({} satır)",
                    sha256, cardLast4, ym, existing.size());
            return new StatementPreviewResponse(
                    sha256, card.getLastFour(), ym.toString(),
                    existing.stream().map(StatementTxnDto::from).toList(),
                    List.of("Bu ekstre zaten yüklendi ve onaylandı; mükerrer satır oluşturulmadı."),
                    true);
        }

        // Dosyayı denetim için sakla (best-effort; parse'i bloklamaz).
        statementStorageService.storeQuietly(content, filename, sha256, ym);

        ParseResult result = statementParser.parse(content, filename, cardLast4);

        List<RawTransaction> persisted = new ArrayList<>();
        String firstWarning = result.warnings().isEmpty() ? null : result.warnings().get(0);
        for (ParsedTxn t : result.transactions()) {
            RawTransaction rt = new RawTransaction();
            rt.setCard(card);
            rt.setPeriod(period);
            rt.setTransactionDate(t.transactionDate());
            rt.setDescription(t.description());
            rt.setAmount(scale2(t.amount()));
            rt.setCurrency(t.currency() != null ? t.currency() : Currency.TRY);
            rt.setAmountTry(scale2(t.amountTry()));
            rt.setStatus(RawTxnStatus.PENDING);
            rt.setSourceFileSha256(sha256);
            rt.setSourceFileName(filename);
            rt.setRawText(t.rawText());
            rt.setParseWarning(firstWarning);
            rt.setMatched(false);
            persisted.add(rawTransactionRepository.save(rt));
        }

        log.info("Statement upload: sha256={} card={} month={} parsed={} warnings={}",
                sha256, cardLast4, ym, persisted.size(), result.warnings().size());

        return new StatementPreviewResponse(
                sha256, card.getLastFour(), ym.toString(),
                persisted.stream().map(StatementTxnDto::from).toList(),
                result.warnings(),
                false);
    }

    /** Bir batch'in PENDING satırlarını CONFIRMED yapar; etkilenen sayıyı döner. */
    @Transactional
    public StatementBatchActionResponse confirm(String batchRef) {
        return transition(batchRef, RawTxnStatus.PENDING, RawTxnStatus.CONFIRMED);
    }

    /** Bir batch'in PENDING satırlarını DISCARDED yapar; etkilenen sayıyı döner. */
    @Transactional
    public StatementBatchActionResponse discard(String batchRef) {
        return transition(batchRef, RawTxnStatus.PENDING, RawTxnStatus.DISCARDED);
    }

    /** Bir batch'in tüm satırlarını (yeniden önizleme için) döner. */
    @Transactional(readOnly = true)
    public StatementPreviewResponse getBatch(String batchRef) {
        List<RawTransaction> rows = rawTransactionRepository.findBySourceFileSha256(batchRef);
        if (rows.isEmpty()) {
            throw new StatementUploadException("Batch bulunamadı: " + batchRef);
        }
        RawTransaction sample = rows.get(0);
        Card card = sample.getCard();
        Period period = sample.getPeriod();
        return new StatementPreviewResponse(
                batchRef,
                card != null ? card.getLastFour() : null,
                period != null ? period.getCode() : null,
                rows.stream().map(StatementTxnDto::from).toList(),
                List.of(),
                false);
    }

    // ------------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------------

    private StatementBatchActionResponse transition(String batchRef, RawTxnStatus from, RawTxnStatus to) {
        if (batchRef == null || batchRef.isBlank()) {
            throw new StatementUploadException("batchRef zorunludur.");
        }
        List<RawTransaction> rows =
                rawTransactionRepository.findBySourceFileSha256AndStatus(batchRef, from);
        for (RawTransaction rt : rows) {
            rt.setStatus(to);
            rawTransactionRepository.save(rt);
        }
        log.info("Statement batch {} -> {}: batchRef={} count={}", from, to, batchRef, rows.size());
        return new StatementBatchActionResponse(batchRef, rows.size());
    }

    private Card resolveCard(String cardLast4) {
        if (cardLast4 == null || cardLast4.isBlank()) {
            throw new StatementUploadException("cardLast4 zorunludur.");
        }
        return cardRepository.findByLastFour(cardLast4.trim())
                .orElseThrow(() -> new StatementUploadException("Bilinmeyen kart: " + cardLast4));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new StatementUploadException("month zorunludur (YYYY-MM).");
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (RuntimeException ex) {
            throw new StatementUploadException("month YYYY-MM biçiminde olmalıdır: " + month);
        }
    }

    private Period resolveOrCreatePeriod(YearMonth ym) {
        String code = ym.toString();
        return periodRepository.findByCode(code).orElseGet(() -> {
            Period p = new Period();
            p.setYear(ym.getYear());
            p.setMonth(ym.getMonthValue());
            p.setCode(code);
            return periodRepository.save(p);
        });
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new StatementUploadException("Yüklenen dosya okunamadı.");
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new StatementUploadException("SHA-256 algoritması bulunamadı.");
        }
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Yüklenen dosya adından tüm yol bileşenlerini sıyırır (path-traversal koruması).
     * "../../etc/passwd" → "passwd", "C:\\tmp\\a.xlsx" → "a.xlsx". null → "".
     */
    private static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        // Hem POSIX (/) hem Windows (\) ayraçlarını normalize et, sonra son bileşeni al.
        String normalized = filename.replace('\\', '/');
        String name = Paths.get(normalized).getFileName().toString();
        return name == null ? "" : name;
    }
}
