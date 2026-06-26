package com.ecommint.accounthr.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.Team;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.dto.PagedResponse;
import com.ecommint.accounthr.dto.service.ServiceContactDto;
import com.ecommint.accounthr.dto.service.ServiceDto;
import com.ecommint.accounthr.dto.service.ServiceResponse;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * {@code Service} entity'leri için salt-okunur sorgu/eşleme katmanı (E1-07 + E3-02).
 *
 * <p>Eşleme {@code @Transactional(readOnly = true)} içinde yapılır ki lazy
 * ilişkiler (provider/defaultCard/usingTeam/contacts) açık session içinde güvenle
 * okunup DTO'ya indirgensin; entity hiç dışarı sızmaz.
 *
 * <p>E3-02: Servisler ekranı için {@link #search(ActiveState, Frequency, String,
 * Pageable)} filtre (aktif/frekans) + arama (isim/sağlayıcı) destekler ve fatura
 * iletişim kayıtlarını da içeren {@link ServiceResponse} döner.
 */
@Service
public class ServiceQueryService {

    private final ServiceRepository serviceRepository;
    private final ServiceContactRepository serviceContactRepository;

    public ServiceQueryService(ServiceRepository serviceRepository,
            ServiceContactRepository serviceContactRepository) {
        this.serviceRepository = serviceRepository;
        this.serviceContactRepository = serviceContactRepository;
    }

    /** E1-07 referans listesi — basit sayfalı {@link ServiceDto} (geriye dönük uyum). */
    @Transactional(readOnly = true)
    public PagedResponse<ServiceDto> list(Pageable pageable) {
        Page<com.ecommint.accounthr.domain.Service> page = serviceRepository.findAll(pageable);
        return PagedResponse.from(page, ServiceQueryService::toDto);
    }

    /**
     * E3-02 Servisler ekranı — opsiyonel filtre (aktiflik/frekans) ve arama
     * (isim/sağlayıcı, büyük/küçük harf duyarsız) ile sayfalı {@link ServiceResponse}.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ServiceResponse> search(ActiveState active, Frequency frequency,
            String q, Pageable pageable) {
        Specification<com.ecommint.accounthr.domain.Service> spec = buildSpec(active, frequency, q);
        Page<com.ecommint.accounthr.domain.Service> page = serviceRepository.findAll(spec, pageable);
        return PagedResponse.from(page, this::toResponse);
    }

    private Specification<com.ecommint.accounthr.domain.Service> buildSpec(
            ActiveState active, Frequency frequency, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("activeState"), active));
            }
            if (frequency != null) {
                predicates.add(cb.equal(root.get("frequency"), frequency));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                Join<Object, Object> provider = root.join("provider", JoinType.LEFT);
                Predicate byName = cb.like(cb.lower(root.get("name")), like);
                Predicate byProvider = cb.like(cb.lower(provider.get("name")), like);
                predicates.add(cb.or(byName, byProvider));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Entity → zengin {@link ServiceResponse} (contacts dahil). Açık transaction içinde. */
    ServiceResponse toResponse(com.ecommint.accounthr.domain.Service s) {
        Provider provider = s.getProvider();
        Card card = s.getDefaultCard();
        Team team = s.getUsingTeam();
        List<ServiceContactDto> contacts = serviceContactRepository.findByServiceId(s.getId()).stream()
                .map(ServiceQueryService::toContactDto)
                .toList();
        return new ServiceResponse(
                s.getId(),
                s.getName(),
                provider != null ? provider.getName() : null,
                card != null ? card.getLastFour() : null,
                team != null ? team.getName() : null,
                s.getFrequency(),
                s.getActiveState(),
                s.getActiveMonths(),
                s.getApproxAmountTry(),
                s.isInformational(),
                s.getInvoiceSource(),
                s.getPurpose(),
                s.getNotes(),
                contacts,
                s.getCreatedAt(),
                s.getUpdatedAt());
    }

    static ServiceContactDto toContactDto(ServiceContact c) {
        return new ServiceContactDto(c.getEmail(), c.getSource(), c.isPrimary());
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
