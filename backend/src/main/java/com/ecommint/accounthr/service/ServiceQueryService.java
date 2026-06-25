package com.ecommint.accounthr.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.dto.PagedResponse;
import com.ecommint.accounthr.dto.service.ServiceDto;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * {@code Service} entity'leri için salt-okunur sorgu/eşleme katmanı (E1-07).
 *
 * <p>Eşleme {@code @Transactional(readOnly = true)} içinde yapılır ki lazy
 * ilişkiler (provider/defaultCard/usingTeam) açık session içinde güvenle okunup
 * DTO'ya indirgensin; entity hiç dışarı sızmaz.
 */
@Service
public class ServiceQueryService {

    private final ServiceRepository serviceRepository;

    public ServiceQueryService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ServiceDto> list(Pageable pageable) {
        Page<com.ecommint.accounthr.domain.Service> page = serviceRepository.findAll(pageable);
        return PagedResponse.from(page, ServiceQueryService::toDto);
    }

    /** Entity → DTO. Açık transaction içinde çağrılmalı (lazy ilişkiler okunur). */
    static ServiceDto toDto(com.ecommint.accounthr.domain.Service s) {
        Provider provider = s.getProvider();
        Card card = s.getDefaultCard();
        Team team = s.getUsingTeam();
        return new ServiceDto(
                s.getId(),
                s.getName(),
                provider != null ? provider.getName() : null,
                card != null ? card.getLastFour() : null,
                team != null ? team.getName() : null,
                s.getFrequency(),
                s.getActiveState(),
                s.isInformational(),
                s.getApproxAmountTry(),
                s.getInvoiceSource(),
                s.getPurpose(),
                s.getNotes(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
