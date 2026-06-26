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
        List<Expense> mainContent = mainPage.getContent();
        Map<Long, Invoice> repInvoices = representativeInvoices(mainContent);
        PagedResponse<ExpenseRow> main = PagedResponse.from(
                mainPage, mainContent,
                e -> toRow(e, repInvoices.get(e.getId())));

        // Operasyonel toplam: dönem geneli (E2-05), filtreden bağımsız.
        BigDecimal operationalTotalTry = orZero(
                expenseRepository.sumMainAmountTryByPeriod(periodId));

        // Bilgi-amaçlı satırlar: AYRI liste, operasyonel toplama dahil değil.
        List<Expense> infoExpenses = expenseRepository.findInformationalByPeriod(periodId);
        Map<Long, Invoice> infoReps = representativeInvoices(infoExpenses);
        List<ExpenseRow> informationalRows = new ArrayList<>(infoExpenses.size());
        for (Expense e : infoExpenses) {
            informationalRows.add(toRow(e, infoReps.get(e.getId())));
        }
        BigDecimal informationalTotalTry = orZero(
                expenseRepository.sumInformationalAmountTryByPeriod(periodId));

        return new ExpenseListResponse(month, main, operationalTotalTry,
                informationalRows, informationalTotalTry);
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
    private ExpenseRow toRow(Expense e, Invoice rep) {
        com.ecommint.accounthr.domain.Service service = e.getService();
        Provider provider = service != null ? service.getProvider() : null;
        Card card = e.getCard();
        Team team = e.getUsingTeam();

        InvoiceStatus invoiceStatus = rep != null ? rep.getStatus() : null;
        String invoiceColorHex = invoiceStatus != null
                ? StatusColors.STATUS_TO_HEX.get(invoiceStatus) : null;
        String invoiceNote = rep != null ? rep.getNote() : null;

        String accountingEmail = service != null ? primaryEmail(service.getId()) : null;

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
                invoiceNote);
    }

    /**
     * Servisin "Muhasebe E-posta" değeri: birincil ({@code isPrimary=true}) iletişim
     * e-postası; yoksa ilk e-postası olan iletişim; hiç yoksa {@code null}.
     */
    private String primaryEmail(Long serviceId) {
        List<ServiceContact> contacts = serviceContactRepository.findByServiceId(serviceId);
        ServiceContact primary = contacts.stream()
                .filter(ServiceContact::isPrimary)
                .filter(c -> StringUtils.hasText(c.getEmail()))
                .findFirst()
                .orElseGet(() -> contacts.stream()
                        .filter(c -> StringUtils.hasText(c.getEmail()))
                        .findFirst()
                        .orElse(null));
        return primary != null ? primary.getEmail() : null;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
