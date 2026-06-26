package com.ecommint.accounthr.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecommint.accounthr.domain.ServiceContact;

public interface ServiceContactRepository extends JpaRepository<ServiceContact, Long> {

    List<ServiceContact> findByServiceId(Long serviceId);

    /**
     * E3-03 N+1 fix — Verilen servisler için TÜM iletişim kayıtları, tek toplu sorgu
     * (per-row {@code findByServiceId} yerine). {@code isPrimary DESC} sırasıyla gelir
     * ki servis katmanındaki "birincil yoksa ilk" indirgemesi deterministik olsun
     * (primary kayıtlar önce). Boş koleksiyon → boş liste.
     */
    @Query("SELECT c FROM ServiceContact c WHERE c.service.id IN :serviceIds "
            + "ORDER BY c.primary DESC, c.id ASC")
    List<ServiceContact> findByServiceIdIn(@Param("serviceIds") Collection<Long> serviceIds);
}
