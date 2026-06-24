package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByPeriodId(Long periodId);

    /** "Bu servisin bu ay bir satırı var mı?" — eksik fatura çapraz doğrulaması. */
    boolean existsByServiceIdAndPeriodId(Long serviceId, Long periodId);
}
