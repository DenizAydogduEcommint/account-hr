package com.ecommint.accounthr.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockMultipartFile;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.RawTransaction;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.RawTransactionRepository;
import com.ecommint.accounthr.service.statement.ParseResult;
import com.ecommint.accounthr.service.statement.ParsedTxn;
import com.ecommint.accounthr.service.statement.StatementParser;
import com.ecommint.accounthr.service.statement.StatementStorageService;
import com.ecommint.accounthr.service.statement.StatementUploadService;

/**
 * E4-01 review fix'leri — servis seviyesi birim testleri.
 *
 * <p>Parser stub'lanır (gerçek placeholder boş döndüğü için persist yolu IT'de
 * gözlemlenemez): bir {@link ParsedTxn} döndürerek upload'ın persist yolunu tetikler ve
 * kaydedilen {@link RawTransaction}'ı {@link ArgumentCaptor} ile yakalarız.
 *
 * <ul>
 *   <li><b>Fix 4:</b> {@code ../../etc/passwd.xlsx} gibi path-traversal içeren dosya adı,
 *       persist edilen {@code sourceFileName}'de yol ayracı içermez ("passwd.xlsx").</li>
 *   <li><b>Fix 2:</b> parser'ın {@code rawText}'i {@link RawTransaction}'a yazılır.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class StatementUploadServiceTest {

    private static final String CARD_LAST4 = "3800";
    private static final String MONTH = "2026-04";

    @Mock private CardRepository cardRepository;
    @Mock private PeriodRepository periodRepository;
    @Mock private RawTransactionRepository rawTransactionRepository;
    @Mock private StatementParser statementParser;
    @Mock private StatementStorageService statementStorageService;

    private StatementUploadService service;

    @BeforeEach
    void setUp() {
        service = new StatementUploadService(cardRepository, periodRepository,
                rawTransactionRepository, statementParser, statementStorageService);

        Card card = new Card();
        card.setId(1L);
        card.setBank("Akbank");
        card.setLastFour(CARD_LAST4);
        when(cardRepository.findByLastFour(CARD_LAST4)).thenReturn(Optional.of(card));

        Period period = new Period();
        period.setId(2L);
        period.setYear(2026);
        period.setMonth(4);
        period.setCode(MONTH);
        when(periodRepository.findByCode(MONTH)).thenReturn(Optional.of(period));

        // Idempotency kapalı → persist yolu çalışsın.
        when(rawTransactionRepository.existsBySourceFileSha256AndCardIdAndPeriodIdAndStatus(
                anyString(), any(), any(), any())).thenReturn(false);
        when(rawTransactionRepository.save(any(RawTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Parser tek bir gerçek satır döndürsün (rawText taşır).
        ParsedTxn txn = new ParsedTxn(
                LocalDate.of(2026, 4, 1), "CLAUDE.AI", new BigDecimal("100.00"),
                Currency.TRY, new BigDecimal("100.00"), "RAW LINE 01.04.2026 CLAUDE.AI 100,00");
        when(statementParser.parse(any(), anyString(), anyString()))
                .thenReturn(new ParseResult(List.of(txn), List.of()));
    }

    @Test
    void uploadSanitizesPathTraversalFilenameAndPersistsRawText() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy-content".getBytes());

        service.upload(file, CARD_LAST4, MONTH);

        ArgumentCaptor<RawTransaction> captor = ArgumentCaptor.forClass(RawTransaction.class);
        org.mockito.Mockito.verify(rawTransactionRepository).save(captor.capture());
        RawTransaction saved = captor.getValue();

        // Fix 4: yol ayracı yok, yalnızca dosya adı.
        assertThat(saved.getSourceFileName()).isEqualTo("passwd.xlsx");
        assertThat(saved.getSourceFileName()).doesNotContain("/").doesNotContain("\\");

        // Fix 2: rawText persist edildi.
        assertThat(saved.getRawText()).isEqualTo("RAW LINE 01.04.2026 CLAUDE.AI 100,00");
    }

    @Test
    void uploadWithWindowsPathFilenameIsSanitized() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "C:\\Users\\evil\\stmt.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy-content".getBytes());

        service.upload(file, CARD_LAST4, MONTH);

        ArgumentCaptor<RawTransaction> captor = ArgumentCaptor.forClass(RawTransaction.class);
        org.mockito.Mockito.verify(rawTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceFileName()).isEqualTo("stmt.xlsx");
    }
}
