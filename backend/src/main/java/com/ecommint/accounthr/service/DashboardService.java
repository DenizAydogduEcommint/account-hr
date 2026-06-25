package com.ecommint.accounthr.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.dashboard.DashboardSummary;
import com.ecommint.accounthr.dto.dashboard.DashboardSummary.StatusCount;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.service.importer.StatusColors;

/**
 * E3-01 — Aylık dashboard özeti hesaplama servisi (salt-okunur).
 *
 * <p>Tüm aggregation DB tarafında (JPQL SUM/COUNT/GROUP BY) yapılır; ham invoice/expense
 * satırları uygulamaya çekilip client-side toplanMAZ. Bilinmeyen/boş ay {@link #getSummary}
 * içinde sıfırlanmış özetle karşılanır (exception fırlatmaz).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** Donut lejantı stabil kalsın diye statusCounts'ta her zaman bu sabit sıra kullanılır. */
    private static final List<InvoiceStatus> STATUS_ORDER = List.of(
            InvoiceStatus.FOUND,
            InvoiceStatus.E_INVOICE,
            InvoiceStatus.EXPECTED,
            InvoiceStatus.TO_INVESTIGATE,
            InvoiceStatus.IGNORED);

    private final PeriodRepository periodRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;

    public DashboardService(PeriodRepository periodRepository,
            ExpenseRepository expenseRepository,
            InvoiceRepository invoiceRepository) {
        this.periodRepository = periodRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Verilen ay kodu ({@code YYYY-MM}) için özet üretir.
     *
     * <p>{@code month} null/boş ya da bilinmeyen bir dönemse: tüm değerler 0/ZERO,
     * {@code statusCounts} 5 durumun tamamı (hepsi 0) ile döner ve {@code month}
     * istek değeri olduğu gibi yansıtılır. Hiçbir durumda exception fırlatılmaz.
     */
    public DashboardSummary getSummary(String month) {
        String echoMonth = month == null ? "" : month.trim();

        if (echoMonth.isBlank()) {
            return zeroedSummary(echoMonth);
        }

        Period period = periodRepository.findByCode(echoMonth).orElse(null);
        if (period == null) {
            return zeroedSummary(echoMonth);
        }

        Long periodId = period.getId();

        BigDecimal totalTry = expenseRepository.sumMainAmountTryByPeriod(periodId);
        if (totalTry == null) {
            totalTry = BigDecimal.ZERO;
        }
        long expenseCount = expenseRepository.countMainExpensesByPeriod(periodId);

        Map<InvoiceStatus, Long> counts = toCountMap(
                invoiceRepository.countGroupByStatusForPeriod(periodId));

        return buildSummary(echoMonth, totalTry, expenseCount, counts);
    }

    /** Bilinmeyen/boş ay: tüm değerler sıfır, statusCounts 5 girdi (hepsi 0). */
    private DashboardSummary zeroedSummary(String month) {
        return buildSummary(month, BigDecimal.ZERO, 0L, emptyCountMap());
    }

    /** {@code [InvoiceStatus, Long]} satırlarını eksik durumları 0'a tamamlayan map'e çevirir. */
    private Map<InvoiceStatus, Long> toCountMap(List<Object[]> rows) {
        Map<InvoiceStatus, Long> counts = emptyCountMap();
        for (Object[] row : rows) {
            InvoiceStatus status = (InvoiceStatus) row[0];
            long count = ((Number) row[1]).longValue();
            counts.put(status, count);
        }
        return counts;
    }

    private Map<InvoiceStatus, Long> emptyCountMap() {
        Map<InvoiceStatus, Long> counts = new EnumMap<>(InvoiceStatus.class);
        for (InvoiceStatus status : InvoiceStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }

    private DashboardSummary buildSummary(String month, BigDecimal totalTry, long expenseCount,
            Map<InvoiceStatus, Long> counts) {
        List<StatusCount> statusCounts = new ArrayList<>(STATUS_ORDER.size());
        for (InvoiceStatus status : STATUS_ORDER) {
            statusCounts.add(new StatusCount(
                    status, counts.get(status), StatusColors.STATUS_TO_HEX.get(status)));
        }

        long missingCount = counts.get(InvoiceStatus.EXPECTED);
        long foundCount = counts.get(InvoiceStatus.FOUND) + counts.get(InvoiceStatus.E_INVOICE);
        long investigateCount = counts.get(InvoiceStatus.TO_INVESTIGATE);

        return new DashboardSummary(
                month, totalTry, statusCounts,
                missingCount, foundCount, investigateCount, expenseCount);
    }
}
