package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecommint.accounthr.domain.Service;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    /**
     * "Her ay beklenen" servisler — eksik fatura tespitinin temeli.
     * Örn: findByActiveStateAndFrequency(ActiveState.YES, Frequency.MONTHLY).
     */
    List<Service> findByActiveStateAndFrequency(ActiveState activeState, Frequency frequency);
}
