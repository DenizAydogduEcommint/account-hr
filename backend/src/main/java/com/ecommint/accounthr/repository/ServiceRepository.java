package com.ecommint.accounthr.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * E3-04 — Eksik fatura çapraz doğrulamasının ADAY kümesi: {@code activeState=YES},
     * {@code informational=false} ve frekansı verilen frekanslardan biri olan servisler
     * (pratikte {@code MONTHLY} ve {@code YEARLY}). Provider + varsayılan kart tek sorguda
     * eager-fetch'lenir (DTO eşlemesinde lazy/N+1 olmasın diye). Yıllık servisler için
     * "Aktif Aylar" filtresi servis katmanında uygulanır (verbatim string parse). İsme göre
     * sıralı döner ki ekran/test sonucu deterministik olsun.
     */
    @Query("SELECT s FROM Service s "
            + "LEFT JOIN FETCH s.provider "
            + "LEFT JOIN FETCH s.defaultCard "
            + "WHERE s.activeState = :activeState "
            + "AND s.informational = false "
            + "AND s.frequency IN :frequencies "
            + "ORDER BY s.name ASC, s.id ASC")
    List<Service> findActiveCheckCandidates(
            @Param("activeState") ActiveState activeState,
            @Param("frequencies") Collection<Frequency> frequencies);

    /** Bir sağlayıcıya bağlı tüm servisler — importer'ın normalize-isim eşlemesi için. */
    List<Service> findByProviderId(Long providerId);
}
