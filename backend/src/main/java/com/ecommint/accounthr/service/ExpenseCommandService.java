package com.ecommint.accounthr.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.ExpenseSource;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.expense.ExpenseCreateRequest;
import com.ecommint.accounthr.dto.expense.ExpenseRow;
import com.ecommint.accounthr.dto.expense.StatusUpdateRequest;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.repository.TeamRepository;

/**
 * E3-06 — Elle (manuel) harcama satırı oluşturma komut katmanı. Kimliği doğrulanmış bir
 * kullanıcının, banka ekstresinden ÖNCE veya ekstre olmadan tek bir harcama satırı
 * girmesini sağlar (servis-tabanlı).
 *
 * <h2>find-or-create mantığı (E2-01 importer / E3-05 yükleme ile aynı desen)</h2>
 * <ul>
 *   <li><b>Service</b>: {@code serviceId} ile bulunur; yoksa 404. Sağlayıcı servisten
 *       türetilir.</li>
 *   <li><b>Period</b>: {@code transactionDate}'in ayından ({@code YYYY-MM}) find-or-create
 *       edilir (importer'la aynı). Eksik fatura çapraz doğrulaması period'a bağlı olduğundan
 *       period'un var olması şarttır.</li>
 *   <li><b>Card</b>: {@code cardLast4} verilmişse o kartla eşleşmeli (bilinmeyen → 400);
 *       verilmemişse servisin varsayılan kartına düşülür (o da yoksa kartsız satır).</li>
 *   <li><b>Team</b>: {@code usingTeamId} verilmişse var olan bir takım olmalı (bilinmeyen
 *       → 400).</li>
 * </ul>
 *
 * <p>Oluşturulan satır {@code source=MANUAL} ile işaretlenir (ekstreden gelen STATEMENT
 * satırlarından ayırt edilebilsin diye) ve durumu "Bekleniyor" ({@link InvoiceStatus#EXPECTED})
 * olan tek bir taslak invoice ile gelir — importer'ın expense+invoice oluşturma desenini
 * yansıtır. {@code source_row_hash} NULL bırakılır: elle satırlar import idempotency'sinin
 * parçası DEĞİLDİR (kolon V6'dan beri nullable).
 *
 * <p>Yanıt, GET {@code /api/v1/expenses}'in döndürdüğüyle BİREBİR aynı {@link ExpenseRow}'dur
 * ({@link ExpenseQueryService#buildRow}), böylece yeni satır frontend'de aynı bileşenle
 * render edilir.
 */
@Service
public class ExpenseCommandService {

    private final ServiceRepository serviceRepository;
    private final PeriodRepository periodRepository;
    private final CardRepository cardRepository;
    private final TeamRepository teamRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final ExpenseQueryService expenseQueryService;
    private final InvoiceStatusPolicy statusPolicy;

    public ExpenseCommandService(ServiceRepository serviceRepository,
            PeriodRepository periodRepository,
            CardRepository cardRepository,
            TeamRepository teamRepository,
            ExpenseRepository expenseRepository,
            InvoiceRepository invoiceRepository,
            ExpenseQueryService expenseQueryService,
            InvoiceStatusPolicy statusPolicy) {
        this.serviceRepository = serviceRepository;
        this.periodRepository = periodRepository;
        this.cardRepository = cardRepository;
        this.teamRepository = teamRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.expenseQueryService = expenseQueryService;
        this.statusPolicy = statusPolicy;
    }

    /**
     * E3-07 — Bir harcama satırının fatura durumunu elle değiştirir.
     *
     * <p>Durum, satırın TEMSİLCİ invoice'unda (en güncel = en yüksek id'li; GET listesinin
     * gösterdiğiyle AYNI tanım, {@link ExpenseQueryService}) güncellenir. Geçiş önce
     * {@link InvoiceStatusPolicy} state machine'inden geçirilir; MVP'de politika permissive
     * (tüm geçişler serbest) olduğundan reddetme olmaz, ancak ileride bir geçiş kapatılırsa
     * {@link IllegalStatusTransitionException} (→ 409) atılır.
     *
     * <p><b>Audit:</b> durum alanının değişimi {@code AuditInterceptor} tarafından otomatik
     * yakalanır → bu transaction commit'inde {@code audit_log}'a {@code STATUS_CHANGE} satırı
     * (changed_by = SecurityContext kullanıcısı, changed_at, status eski→yeni) yazılır. Elle
     * paralel audit yok.
     *
     * <p><b>Invoice yoksa:</b> Hem E2-01 importer hem E3-06 elle giriş her expense için en az
     * bir invoice oluşturur; bu bir veri-bütünlüğü değişmezidir (her expense'in ≥1 invoice'u
     * vardır) ve invoice'suz bir expense BEKLENMEZ. Bu durum sessizce iyileştirilmez (yeni
     * invoice OLUŞTURULMAZ — eşzamanlı PATCH'lerde DB güvencesi olmayan yetim-invoice riski
     * doğururdu); bunun yerine fail-fast olarak {@link ResourceNotFoundException} (→ 404)
     * ile yüzeye çıkarılır.
     *
     * <p><b>IGNORED ↔ informational bağımsızlığı:</b> Bu metot YALNIZCA invoice durumunu
     * değiştirir. Expense'in {@code informational} bayrağı (operasyonel toplama dahil/hariç)
     * AYRI bir kavramdır ve burada DOKUNULMAZ. IGNORED'a geçmek operasyonel toplamı
     * değiştirmez (toplam {@code informational} bayrağına bağlıdır, invoice durumuna değil —
     * E2-05/E3-03). Bu ayrım bilinçlidir.
     *
     * @throws ResourceNotFoundException         expense yoksa (404)
     * @throws IllegalStatusTransitionException   geçiş politikaca yasaksa (409; MVP'de olmaz)
     */
    @Transactional
    public ExpenseRow updateStatus(Long expenseId, StatusUpdateRequest request) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Harcama bulunamadı: id=" + expenseId));

        InvoiceStatus target = request.status();
        Invoice representative = invoiceRepository
                .findFirstByExpenseIdOrderByIdDesc(expenseId)
                .orElse(null);

        InvoiceStatus current = representative != null ? representative.getStatus() : null;
        if (!statusPolicy.isAllowed(current, target)) {
            throw new IllegalStatusTransitionException(current, target);
        }

        if (representative == null) {
            // Veri-bütünlüğü değişmezi: her expense'in ≥1 invoice'u olmalı (importer + E3-06
            // garanti eder). Invoice'suz expense BEKLENMEZ — sessizce iyileştirmek yerine
            // (eşzamanlı PATCH yetim-invoice riski) fail-fast 404 ile yüzeye çıkar.
            throw new ResourceNotFoundException(
                    "Bu satır için fatura kaydı bulunamadı.");
        }
        // Durum alanını güncelle — değişim AuditInterceptor ile STATUS_CHANGE'e dönüşür.
        representative.setStatus(target);

        return expenseQueryService.buildRow(expense.getId());
    }

    /**
     * Elle bir harcama satırı (+ EXPECTED taslak invoice) oluşturur ve oluşan satırı GET
     * listesiyle aynı {@link ExpenseRow} olarak döner. Tek transaction.
     *
     * @throws ResourceNotFoundException        {@code serviceId} bilinmiyorsa (404)
     * @throws InvalidExpenseRequestException   {@code cardLast4}/{@code usingTeamId} bilinmiyorsa (400)
     */
    @Transactional
    public ExpenseRow create(ExpenseCreateRequest request) {
        com.ecommint.accounthr.domain.Service service = serviceRepository
                .findById(request.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Servis bulunamadı: id=" + request.serviceId()));
        Provider provider = service.getProvider();

        Period period = resolveOrCreatePeriod(request.transactionDate());

        Card card = resolveCard(request.cardLast4(), service);
        Team team = resolveTeam(request.usingTeamId());

        Expense expense = new Expense();
        expense.setService(service);
        expense.setPeriod(period);
        expense.setCard(card);
        expense.setUsingTeam(team);
        expense.setTransactionDate(request.transactionDate());
        expense.setAmount(request.amount());
        expense.setCurrency(request.currency());
        expense.setAmountTry(request.amountTry());
        expense.setInformational(request.informational());
        expense.setPurpose(blankToNull(request.purpose()));
        expense.setSource(ExpenseSource.MANUAL);
        // Elle satırlar import idempotency'sinin parçası değil → hash NULL (kolon nullable).
        expense.setSourceRowHash(null);
        expense = expenseRepository.save(expense);

        // Taslak invoice: durum "Bekleniyor" (EXPECTED) — fatura henüz toplanmadı.
        Invoice invoice = new Invoice();
        invoice.setExpense(expense);
        invoice.setProvider(provider);
        invoice.setStatus(InvoiceStatus.EXPECTED);
        invoice.setCurrency(request.currency());
        invoice.setAmount(request.amount());
        invoice.setRefund(false);
        invoiceRepository.save(invoice);

        return expenseQueryService.buildRow(expense.getId());
    }

    /**
     * {@code transactionDate}'in ayından ({@code YYYY-MM}) period'u bulur; yoksa oluşturur
     * (E2-01 importer / E3-05 yükleme ile aynı desen).
     */
    private Period resolveOrCreatePeriod(java.time.LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        String code = String.format("%04d-%02d", year, month);
        return periodRepository.findByCode(code).orElseGet(() -> {
            Period p = new Period();
            p.setYear(year);
            p.setMonth(month);
            p.setCode(code);
            return periodRepository.save(p);
        });
    }

    /**
     * Kart çözümü: {@code cardLast4} verilmişse bilinen bir kartla eşleşmeli (yoksa 400);
     * verilmemişse servisin varsayılan kartına düşülür (o da yoksa null = kartsız satır).
     */
    private Card resolveCard(String cardLast4, com.ecommint.accounthr.domain.Service service) {
        if (StringUtils.hasText(cardLast4)) {
            String trimmed = cardLast4.trim();
            return cardRepository.findByLastFour(trimmed)
                    .orElseThrow(() -> new InvalidExpenseRequestException(
                            "Bilinmeyen kart son-4 hanesi: " + trimmed));
        }
        return service.getDefaultCard();
    }

    /** Takım çözümü: {@code usingTeamId} verilmişse var olan bir takım olmalı (yoksa 400). */
    private Team resolveTeam(Long usingTeamId) {
        if (usingTeamId == null) {
            return null;
        }
        return teamRepository.findById(usingTeamId)
                .orElseThrow(() -> new InvalidExpenseRequestException(
                        "Bilinmeyen takım id: " + usingTeamId));
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
