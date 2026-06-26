package com.ecommint.accounthr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ecommint.accounthr.domain.Service;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;

public interface ServiceRepository
        extends JpaRepository<Service, Long>, JpaSpecificationExecutor<Service> {

    /**
     * "Her ay beklenen" servisler — eksik fatura tespitinin temeli.
     * Örn: findByActiveStateAndFrequency(ActiveState.YES, Frequency.MONTHLY).
     */
    List<Service> findByActiveStateAndFrequency(ActiveState activeState, Frequency frequency);

    /** Bir sağlayıcıya bağlı tüm servisler — importer'ın normalize-isim eşlemesi için. */
    List<Service> findByProviderId(Long providerId);
}
