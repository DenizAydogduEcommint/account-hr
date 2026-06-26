package com.ecommint.accounthr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.dto.missing.MissingInvoiceListResponse;
import com.ecommint.accounthr.dto.missing.MissingInvoiceRow;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E3-04 — "Bu ay hangi servisin faturası bekleniyordu ama gelmedi?" sorusunu yanıtlayan
 * servis ↔ ay çapraz doğrulama (KİLİT MVP). Banka ekstresinden değil SERVİSLER master
 * listesinden giderek eksikleri tespit eder.
 *
 * <h2>Kontrol kümesi (bir servis o ay kontrol edilir iff)</h2>
 * <ul>
 *   <li>{@code frequency=MONTHLY} AND {@code activeState=YES} AND {@code informational=false}
 *       → HER ay kontrol edilir; veya</li>
 *   <li>{@code frequency=YEARLY} AND {@code activeState=YES} AND {@code informational=false}
 *       → YALNIZCA seçili ay servisin "Aktif Aylar" (virgüllü {@code YYYY-MM}) listesinde
 *       geçiyorsa kontrol edilir.</li>
 * </ul>
 * Tamamen HARİÇ: {@code USAGE_BASED}, {@code AD_HOC}, {@code activeState != YES},
 * {@code informational = true} (Multinet/sigorta/Claude iade satırları).
 *
 * <h2>Eksik tanımı</h2>
 * Kontrol kümesindeki bir servis, o dönemde durumu {@code FOUND}/{@code E_INVOICE} olan
 * hiçbir harcaması YOKSA eksiktir (Bekleniyor = henüz fatura yok = eksik; hiç satır olmaması
 * da eksik). Tek-sorgu yaklaşımı: (1) aday kümesi tek sorgu, (2) o dönemde FOUND/E_INVOICE
 * harcaması olan servis ID'leri tek sorgu → küme-farkı. İletişim e-postaları ve
 * "en son görülen ay" toplu sorgularla çözülür (per-servis N+1 yok).
 *
 * <p>{@link com.ecommint.accounthr.service.DashboardService} aynı mantığı paylaşır:
 * dashboard {@code missingCount} = bu servisin {@code findMissing(month).size()} değeridir;
 * iki ekran asla çelişmez (DOD: "eksik sayısı dashboard ile birebir").
 */
@Service
@Transactional(readOnly = true)
public class MissingInvoiceService {

    /** Kontrol kümesinin frekansları: yalnızca Aylık ve Yıllık (Kullanım bazlı/Ad-hoc hariç). */
    private static final List<Frequency> CHECKED_FREQUENCIES = List.of(
            Frequency.MONTHLY, Frequency.YEARLY);

    private final PeriodRepository periodRepository;
    private final ServiceRepository serviceRepository;
    private final ExpenseRepository expenseRepository;
    private final ServiceContactRepository serviceContactRepository;

    public MissingInvoiceService(PeriodRepository periodRepository,
            ServiceRepository serviceRepository,
            ExpenseRepository expenseRepository,
            ServiceContactRepository serviceContactRepository) {
        this.periodRepository = periodRepository;
        this.serviceRepository = serviceRepository;
        this.expenseRepository = expenseRepository;
        this.serviceContactRepository = serviceContactRepository;
    }

    /**
     * Verilen ay ({@code YYYY-MM}) için faturası eksik servis satırlarını döner.
     *
     * <p>{@code month} null/boş ya da bilinmeyen (DB'de period yok) bir dönemse: BOŞ liste
     * döner (500 atmaz — dashboard ile aynı toleranslı davranış). {@code month} bu noktada
     * controller tarafından doğrulanmış/normalize edilmiş kabul edilir.
     */
    public List<MissingInvoiceRow> findMissing(String month) {
        String code = month == null ? "" : month.trim();
        if (code.isBlank()) {
            return List.of();
        }
        Period period = periodRepository.findByCode(code).orElse(null);
        if (period == null) {
            return List.of();
        }
        Long periodId = period.getId();

        // (1) Aday kümesi: Aktif=Evet, bilgi-amaçlı değil, Aylık veya Yıllık (tek sorgu).
        List<com.ecommint.accounthr.domain.Service> candidates =
                serviceRepository.findActiveCheckCandidates(ActiveState.YES, CHECKED_FREQUENCIES);

        // Kontrol kümesi: Aylık her ay; Yıllık yalnızca Aktif Aylar bu ayı içeriyorsa.
        List<com.ecommint.accounthr.domain.Service> checkSet = new ArrayList<>(candidates.size());
        for (com.ecommint.accounthr.domain.Service s : candidates) {
            if (s.getFrequency() == Frequency.MONTHLY) {
                checkSet.add(s);
            } else if (s.getFrequency() == Frequency.YEARLY
                    && activeMonthsContains(s.getActiveMonths(), code)) {
                checkSet.add(s);
            }
        }
        if (checkSet.isEmpty()) {
            return List.of();
        }

        // (2) O dönemde FOUND/E_INVOICE harcaması olan servisler (tek sorgu) → "bulundu" kümesi.
        Set<Long> foundServiceIds = new HashSet<>(
                expenseRepository.findServiceIdsWithFoundInvoiceInPeriod(periodId));

        // Küme-farkı: kontrol kümesinde olup "bulundu" kümesinde olmayanlar EKSİKTİR.
        List<com.ecommint.accounthr.domain.Service> missing = new ArrayList<>();
        for (com.ecommint.accounthr.domain.Service s : checkSet) {
            if (!foundServiceIds.contains(s.getId())) {
                missing.add(s);
            }
        }
        if (missing.isEmpty()) {
            return List.of();
        }

        List<Long> missingIds = missing.stream()
                .map(com.ecommint.accounthr.domain.Service::getId).toList();

        // Birincil iletişim e-postaları (toplu, N+1 yok). isPrimary DESC sıralı → ilk = birincil.
        Map<Long, String> emailByService = primaryEmails(missingIds);
        // "En son görülen ay" (toplu, N+1 yok).
        Map<Long, String> lastSeenByService = lastSeenMonths(missingIds);

        List<MissingInvoiceRow> rows = new ArrayList<>(missing.size());
        for (com.ecommint.accounthr.domain.Service s : missing) {
            Provider provider = s.getProvider();
            Card card = s.getDefaultCard();
            rows.add(new MissingInvoiceRow(
                    s.getId(),
                    s.getName(),
                    provider != null ? provider.getName() : null,
                    card != null ? card.getLastFour() : null,
                    s.getFrequency(),
                    s.getApproxAmountTry(),
                    emailByService.get(s.getId()),
                    s.getActiveMonths(),
                    lastSeenByService.get(s.getId())));
        }
        return rows;
    }

    /**
     * E3-10 — {@link #findMissing(String)} satırlarını KPI zarfına ({@link MissingInvoiceListResponse})
     * sarar: {@code count = items.size()} ve {@code approxTotalTry} = her satırın yaklaşık TL
     * tutarının toplamı (null → 0 katkı). Toplam ölçek 2'ye yuvarlanır (para). Dashboard ile
     * tek kaynak: {@link com.ecommint.accounthr.service.DashboardService} aynı satır kümesini
     * kullanır → sayı ve toplam asla çelişmez.
     */
    public MissingInvoiceListResponse findMissingResponse(String month) {
        List<MissingInvoiceRow> rows = findMissing(month);
        return new MissingInvoiceListResponse(rows, rows.size(), approxTotalTry(rows));
    }

    /**
     * Eksik satırların yaklaşık TL toplamı. {@code approxAmountTry == null} olan satır toplama
     * 0 ekler (ama sayıya dahildir). Sonuç ölçek 2'ye HALF_UP yuvarlanır (money).
     */
    public BigDecimal approxTotalTry(List<MissingInvoiceRow> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (MissingInvoiceRow row : rows) {
            if (row.approxAmountTry() != null) {
                total = total.add(row.approxAmountTry());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * "Aktif Aylar" verbatim string'i ({@code "2026-01, 2026-02, 2026-03"}) verilen ay kodunu
     * içeriyor mu? Virgülle ayrılır, her parça trim'lenir ve tam eşleşme aranır (substring
     * eşleşmesi DEĞİL — ör. "2026-1" yanlışlıkla eşleşmesin). Boş/null → false.
     */
    private boolean activeMonthsContains(String activeMonths, String code) {
        if (!StringUtils.hasText(activeMonths)) {
            return false;
        }
        for (String part : activeMonths.split(",")) {
            if (part.trim().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** Servis → birincil e-posta (varsa); birincil yoksa ilk e-posta. Toplu sorgu, N+1 yok. */
    private Map<Long, String> primaryEmails(List<Long> serviceIds) {
        Map<Long, String> byService = new HashMap<>();
        // isPrimary DESC, id ASC sıralı gelir → ilk karşılaşılan = birincil (yoksa ilk e-posta).
        for (ServiceContact c : serviceContactRepository.findByServiceIdIn(serviceIds)) {
            if (!StringUtils.hasText(c.getEmail())) {
                continue;
            }
            byService.putIfAbsent(c.getService().getId(), c.getEmail());
        }
        return byService;
    }

    /** Servis → en son görüldüğü ay kodu (en büyük period code). Toplu sorgu, N+1 yok. */
    private Map<Long, String> lastSeenMonths(List<Long> serviceIds) {
        Map<Long, String> byService = new HashMap<>();
        for (Object[] row : expenseRepository.findLastSeenMonthByServiceIds(serviceIds)) {
            Long serviceId = ((Number) row[0]).longValue();
            String maxCode = (String) row[1];
            byService.put(serviceId, maxCode);
        }
        return byService;
    }
}
