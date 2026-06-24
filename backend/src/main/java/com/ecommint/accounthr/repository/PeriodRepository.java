package com.ecommint.accounthr.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Period;

public interface PeriodRepository extends JpaRepository<Period, Long> {

    Optional<Period> findByCode(String code);
}
