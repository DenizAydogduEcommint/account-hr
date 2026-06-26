package com.ecommint.accounthr.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.PagedResponse;
import com.ecommint.accounthr.dto.expense.ExpenseListResponse;
import com.ecommint.accounthr.dto.expense.ExpenseRow;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.service.importer.StatusColors;
// ResourceNotFoundException is in the same package (com.ecommint.accounthr.service).

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * E3-03 — Aylık harcamalar ekranı (12 kolonlu tablo) salt-okunur sorgu/eşleme katmanı.
 *
 * <p>Excel ay sheet'inin web karşılığı. ANA (operasyonel, {@code informational=false})
 * harcamalar JPA {@link Specification} ile filtrelenip sayfalanır; bilgi-amaçlı
 * ({@code informational=true}: Multinet/sigorta/vergi) satırlar AYRI bir liste olarak
 * ve operasyonel toplama DAHİL EDİLMEDEN döner.
 *
 * <p>Filtreler yalnızca ANA satırlara uygulanır: {@code card} (son-4), {@code status}
 * (InvoiceStatus — temsilci invoice durumu), {@code q} (hizmet/sağlayıcı adı,
 * büyük/küçük harf duyarsız). Operasyonel toplam dönem geneli E2-05 kuralıyla
 * ({@code sumMainAmountTryByPeriod}) hesaplanır ve filtreden BAĞIMSIZDIR.
 *
 * <p>Eşleme {@code @Transactional(readOnly = true)} içinde yapılır; entity dışarı sızmaz.
 */
@Service
public class ExpenseQueryService {

    private final PeriodRepository periodRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final ServiceContactRepository serviceContactRepository;

    public ExpenseQueryService(PeriodRepository periodRepository,
            ExpenseRepository expenseRepository,
            InvoiceRepository invoiceRepository,
            ServiceContactRepository serviceContactRepository) {
        this.periodRepository = periodRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.serviceContactRepository = serviceContactRepository;
    }

    /**
     * Verilen ay ({@code YYYY-MM}) için ANA harcamaları sayfalı, bilgi-amaçlıları ayrı
     * listeyle döner.
     *
     * <p>Bilinmeyen/var olmayan ay: 500 ATMAZ — boş sayfa + sıfır toplamlar + boş
     * bilgi-amaçlı liste döner (dashboard ile aynı davranış). {@code month} bu noktada
     * controller tarafından doğrulanmış/normalize edilmiş kabul edilir.
     */
    @Transactional(readOnly = true)
    public ExpenseListResponse list(String month, String card, InvoiceStatus status,
            String q, Pageable pageable) {
        Period period = month == null ? null : periodRepository.findByCode(month).orElse(null);

        if (period == null) {
            PagedResponse<ExpenseRow> emptyMain = PagedResponse.from(Page.empty(pageable));
            return new ExpenseListResponse(month, emptyMain, BigDecimal.ZERO,
                    List.of(), BigDecimal.ZERO);
        }

        Long periodId = period.getId();

        // ANA satırlar: Specification (period + informational=false + filtreler) ile sayfalı.
        // Sayfalama + count, koleksiyon FETCH olmadan bu sorgudan gelir (count doğru kalır);
        // ilişkiler (service/provider/card/usingTeam) sayfa içeriği için ID bazlı TEK ek
        // FETCH-JOIN sorgusuyla eager-load edilir (N+1 yok). Servis-iletişim e-postaları da
        // tek toplu sorguyla map'lenir. Böylece toRow döngüsü ek sorgu üretmez.
        Specification<Expense> spec = buildMainSpec(periodId, card, status, q);
        Page<Expense> mainPage = expenseRepository.findAll(spec, pageable);
        List<Expense> mainContent = fetchRefsInPageOrder(mainPage.getContent());
        Map<Long, Invoice> repInvoices = representativeInvoices(mainContent);
        Map<Long, String> mainEmails = primaryEmails(mainContent);
        PagedResponse<ExpenseRow> main = PagedResponse.from(
                mainPage, mainContent,
                e -> toRow(e, repInvoices.get(e.getId()), mainEmails));

        // Operasyonel toplam: dönem geneli (E2-05), filtreden bağımsız.
        BigDecimal operationalTotalTry = orZero(
                expenseRepository.sumMainAmountTryByPeriod(periodId));

        // Bilgi-amaçlı satırlar: AYRI liste, operasyonel toplama dahil değil.
        List<Expense> infoExpenses = expenseRepository.findInformationalByPeriod(periodId);
        Map<Long, Invoice> infoReps = representativeInvoices(infoExpenses);
        Map<Long, String> infoEmails = primaryEmails(infoExpenses);
        List<ExpenseRow> informationalRows = new ArrayList<>(infoExpenses.size());
        for (Expense e : infoExpenses) {
            informationalRows.add(toRow(e, infoReps.get(e.getId()), infoEmails));
        }
        BigDecimal informationalTotalTry = orZero(
                expenseRepository.sumInformationalAmountTryByPeriod(periodId));

        return new ExpenseListResponse(month, main, operationalTotalTry,
                informationalRows, informationalTotalTry);
    }

    /**
     * E3-06 — Tek bir expense'i (id ile) {@link ExpenseRow}'a eşler. Elle satır oluşturma
     * sonrası, GET listesinin döndürdüğüyle BİREBİR aynı satırı yanıt olarak üretmek için
     * kullanılır (frontend aynı bileşenle render eder). Temsilci invoice = en güncel
     * (en yüksek id'li); yeni oluşturulan satırda bu tek EXPECTED invoice'tur. Açık bir
     * okuma transaction'ı içinde çalışır; lazy ilişkiler bu sınır içinde çözülür.
     *
     * @throws ResourceNotFoundException expense yoksa (çağıran sözleşmesi: id geçerli olmalı)
     */
    @Transactional(readOnly = true)
    public ExpenseRow buildRow(Long expenseId) {
        return buildRowInternal(expenseId);
    }

    /**
     * E3-07-DR-1 — {@link #buildRow} satır-kurma/eşleme gövdesi, AÇIK bir transaction
     * sınırı OLMADAN. Tasarımı gereği {@code @Transactional} taşımaz: ya {@link #buildRow}
     * (salt-okunur read yolundan, kendi {@code readOnly=true} tx'i içinde) ya da MEVCUT bir
     * yazma transaction'ı içinden ({@code ExpenseCommandService.updateStatus}/{@code create} —
     * komut yolu) çağrılmalıdır. Böylece komut yolundaki çağrılar dış yazma tx'ine katılır ve
     * yanıltıcı bir iç {@code readOnly} tx illüzyonu doğmaz; davranış (audit flush dahil)
     * değişmez. Doğrudan, transaction'sız bir bağlamdan çağrılmamalıdır (lazy ilişkiler için
     * açık bir sınır gerekir).
     *
     * @throws ResourceNotFoundException expense yoksa (çağıran sözleşmesi: id geçerli olmalı)
     */
    ExpenseRow buildRowInternal(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Harcama bulunamadı: id=" + expenseId));
        List<Expense> one = List.of(expense);
        Map<Long, Invoice> reps = representativeInvoices(one);
        Map<Long, String> emails = primaryEmails(one);
        return toRow(expense, reps.get(expense.getId()), emails);
    }

    /**
     * ANA harcama satırları için filtre specification'ı: zorunlu {@code period.id} +
     * {@code informational=false}, opsiyonel {@code card} (son-4), {@code status} (temsilci
     * invoice durumu — alt sorgu), {@code q} (hizmet/sağlayıcı adı, duyarsız).
     */
    private Specification<Expense> buildMainSpec(Long periodId, String card,
            InvoiceStatus status, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("period").get("id"), periodId));
            predicates.add(cb.isFalse(root.get("informational")));

            if (StringUtils.hasText(card)) {
                Join<Object, Object> cardJoin = root.join("card", JoinType.LEFT);
                predicates.add(cb.equal(cardJoin.get("lastFour"), card.trim()));
            }

            if (status != null) {
                // Temsilci-invoice tutarlılığı: satır rozetinde GÖSTERİLEN durum, expense'in
                // EN GÜNCEL (max-id) invoice'unun durumudur. Filtre de aynı temsilci invoice'u
                // hedeflemeli; yoksa ?status=FOUND, rozeti EXPECTED görünen satırı döndürebilir.
                // EXISTS (i: i.expense = e AND i.status = :status AND i.id = (max id))
                // — tek SQL, N+1 yok.
                jakarta.persistence.criteria.Subquery<Long> sub = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<Invoice> inv = sub.from(Invoice.class);

                jakarta.persistence.criteria.Subquery<Long> maxSub = sub.subquery(Long.class);
                jakarta.persistence.criteria.Root<Invoice> inv2 = maxSub.from(Invoice.class);
                maxSub.select(cb.max(inv2.get("id")));
                maxSub.where(cb.equal(inv2.get("expense"), root));

                sub.select(inv.get("id"));
                sub.where(
                        cb.equal(inv.get("expense"), root),
                        cb.equal(inv.get("status"), status),
                        cb.equal(inv.get("id"), maxSub));
                predicates.add(cb.exists(sub));
            }

            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                Join<Object, Object> service = root.join("service", JoinType.LEFT);
                Join<Object, Object> provider = service.join("provider", JoinType.LEFT);
                Predicate byName = cb.like(cb.lower(service.get("name")), like);
                Predicate byProvider = cb.like(cb.lower(provider.get("name")), like);
                predicates.add(cb.or(byName, byProvider));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * E3-03-1 ToOne N+1 fix — Sayfa içeriğinin ToOne ilişkilerini ({@code service},
     * {@code service.provider}, {@code card}, {@code usingTeam}) TEK FETCH-JOIN sorgusuyla
     * eager-load eder, böylece {@link #toRow} döngüsü bu ilişkilere erişirken per-row SELECT
     * üretmez (önceden bir sayfada ~200+ ek sorgu).
     *
     * <p>Pagination-güvenli iki-sorgu deseni: (1) {@code findAll(spec, pageable)} doğru
     * sayfalama + count üretir (koleksiyon FETCH yok → count/firstResult/maxResults bozulmaz);
     * (2) bu metot o sayfanın ID'leri için {@code findByIdInFetchingRefs} ile ToOne ilişkileri
     * TEK sorguda getirir. Yalnızca {@code *ToOne} fetch edilir (koleksiyon değil), IN listesi
     * sayfa boyutuyla sınırlıdır → "firstResult/maxResults + collection fetch" tuzağı oluşmaz.
     *
     * <p>{@code IN} sonucu sıralı gelmeyebileceğinden, {@code pageable} sort sırası ORİJİNAL
     * sayfa içeriği sırasına göre yeniden kurulur (id→Expense map + sayfa sırasında map'leme).
     * Boş sayfada ek sorgu çalıştırılmaz.
     */
    private List<Expense> fetchRefsInPageOrder(List<Expense> pageContent) {
        if (pageContent.isEmpty()) {
            return pageContent;
        }
        List<Long> ids = pageContent.stream().map(Expense::getId).toList();
        Map<Long, Expense> byId = new HashMap<>();
        for (Expense e : expenseRepository.findByIdInFetchingRefs(ids)) {
            byId.put(e.getId(), e);
        }
        List<Expense> ordered = new ArrayList<>(pageContent.size());
        for (Expense original : pageContent) {
            // Fetch sorgusu sayfadaki her ID'yi döndürmeli; emniyet için map'te yoksa
            // orijinal (lazy) entity'ye düş.
            ordered.add(byId.getOrDefault(original.getId(), original));
        }
        return ordered;
    }

    /**
     * Verilen expense'ler için temsilci invoice eşlemesi ({@code expenseId → en güncel
     * invoice}). Tek toplu sorgu (N+1 yok); {@code id ASC} sırasında "son kazanır" =
     * en yüksek id'li (en güncel) invoice satırda gösterilir. Invoice'u olmayan expense
     * map'te yer almaz (satırda durum/not null olur).
     */
    private Map<Long, Invoice> representativeInvoices(List<Expense> expenses) {
        if (expenses.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = expenses.stream().map(Expense::getId).toList();
        Map<Long, Invoice> byExpense = new HashMap<>();
        for (Invoice inv : invoiceRepository.findByExpenseIdIn(ids)) {
            // id ASC sıralı geldiği için son yazılan = en yüksek id (en güncel) kalır.
            byExpense.put(inv.getExpense().getId(), inv);
        }
        return byExpense;
    }

    /** Entity → {@link ExpenseRow} (12 kolon). Açık transaction içinde çağrılmalı. */
    private ExpenseRow toRow(Expense e, Invoice rep, Map<Long, String> emailByService) {
        com.ecommint.accounthr.domain.Service service = e.getService();
        Provider provider = service != null ? service.getProvider() : null;
        Card card = e.getCard();
        Team team = e.getUsingTeam();

        InvoiceStatus invoiceStatus = rep != null ? rep.getStatus() : null;
        String invoiceColorHex = invoiceStatus != null
                ? StatusColors.STATUS_TO_HEX.get(invoiceStatus) : null;
        String invoiceNote = rep != null ? rep.getNote() : null;

        String accountingEmail = service != null ? emailByService.get(service.getId()) : null;

        return new ExpenseRow(
                e.getId(),
                e.getTransactionDate(),
                service != null ? service.getName() : null,
                provider != null ? provider.getName() : null,
                e.getAmount(),
                e.getCurrency(),
                e.getAmountTry(),
                card != null ? card.getLastFour() : null,
                team != null ? team.getName() : null,
                e.getPurpose(),
                accountingEmail,
                invoiceStatus,
                invoiceColorHex,
                invoiceNote,
                e.getSource());
    }

    /**
     * E3-03 N+1 fix — Sayfa içeriğinin servis ID'leri için "Muhasebe E-posta" eşlemesi,
     * TEK toplu sorguyla ({@code findByServiceIdIn}; per-row {@code findByServiceId} yerine).
     * Birincil ({@code isPrimary=true}) e-posta önce gelir ({@code isPrimary DESC, id ASC});
     * {@code putIfAbsent} ile servis başına ilk geçerli e-posta (birincil yoksa ilk) seçilir.
     * {@code MissingInvoiceService.primaryEmails()} ile aynı desen.
     */
    private Map<Long, String> primaryEmails(List<Expense> expenses) {
        List<Long> serviceIds = expenses.stream()
                .map(Expense::getService)
                .filter(s -> s != null)
                .map(com.ecommint.accounthr.domain.Service::getId)
                .distinct()
                .toList();
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> byService = new HashMap<>();
        for (ServiceContact c : serviceContactRepository.findByServiceIdIn(serviceIds)) {
            if (!StringUtils.hasText(c.getEmail())) {
                continue;
            }
            byService.putIfAbsent(c.getService().getId(), c.getEmail());
        }
        return byService;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
