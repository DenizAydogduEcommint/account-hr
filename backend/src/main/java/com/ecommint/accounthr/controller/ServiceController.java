package com.ecommint.accounthr.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.dto.PagedResponse;
import com.ecommint.accounthr.dto.service.ServiceActiveRequest;
import com.ecommint.accounthr.dto.service.ServiceRequest;
import com.ecommint.accounthr.dto.service.ServiceResponse;
import com.ecommint.accounthr.service.ServiceCommandService;
import com.ecommint.accounthr.service.ServiceQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Servis (abonelik) master liste CRUD uçları (E1-07 referans + E3-02 Servisler ekranı).
 *
 * <p>Standart REST sözleşmesi: çoğul kaynak adı ({@code /api/v1/services}),
 * {@link Pageable} sayfalama/sıralama, entity sızdırmayan {@link ServiceResponse},
 * {@link PagedResponse} zarfı, Swagger dokümantasyonu.
 *
 * <p>Yetki: okuma herhangi bir kimlik doğrulanmış kullanıcıya açık; create/update/
 * deactivate yalnızca ADMIN. Sert silme (hard DELETE) YOKTUR — geçmiş veriyle ilişki
 * korunsun diye {@link #setActive} ile pasifleştirilir.
 */
@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Services", description = "Servis (abonelik) master listesi — CRUD + filtre/arama")
@SecurityRequirement(name = "bearerAuth")
public class ServiceController {

    private final ServiceQueryService serviceQueryService;
    private final ServiceCommandService serviceCommandService;

    public ServiceController(ServiceQueryService serviceQueryService,
            ServiceCommandService serviceCommandService) {
        this.serviceQueryService = serviceQueryService;
        this.serviceCommandService = serviceCommandService;
    }

    /**
     * Servisleri sayfalı listele. Opsiyonel filtreler: {@code ?active=YES|NO|UNCERTAIN},
     * {@code ?frequency=MONTHLY|...}, {@code ?q=} (isim/sağlayıcı arama). Standart
     * sayfalama: {@code ?page=&size=&sort=name,asc}.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Servisleri listele (sayfalı, filtre + arama)",
            description = "Opsiyonel: ?active=YES|NO|UNCERTAIN, ?frequency=MONTHLY|YEARLY|USAGE_BASED|AD_HOC, "
                    + "?q=isim/sağlayıcı. Sayfalama: ?page=&size=&sort=name,asc. "
                    + "Yanıt PagedResponse<ServiceResponse>. Kimlik doğrulama gerektirir.")
    public PagedResponse<ServiceResponse> list(
            @RequestParam(required = false) ActiveState active,
            @RequestParam(required = false) Frequency frequency,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return serviceQueryService.search(active, frequency, q, pageable);
    }

    /** Yeni servis oluştur (yalnızca ADMIN). Sağlayıcı resolve-or-create, kart son-4-haneye göre. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Servis oluştur (ADMIN)",
            description = "Sağlayıcı resolve-or-create, kart son-4-haneye göre çözülür. "
                    + "name zorunlu; her contact e-postası geçerli olmalı (virgüllü çoklu adres destekli).")
    public ServiceResponse create(@Valid @RequestBody ServiceRequest request) {
        return serviceCommandService.create(request);
    }

    /** Mevcut servisi güncelle (yalnızca ADMIN). İletişim kayıtları tamamen yeniden yazılır. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Servis güncelle (ADMIN)",
            description = "Tüm alanlar mutasyona uğrar; contacts listesi tamamen değiştirilir. "
                    + "Servis yoksa 404.")
    public ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceRequest request) {
        return serviceCommandService.update(id, request);
    }

    /**
     * Servisi aktif/pasif yap (yalnızca ADMIN). Sert silme YOKTUR — geçmiş veriyle
     * ilişki bozulmasın diye yalnızca aktiflik durumu değiştirilir.
     */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Servisi aktif/pasif yap (ADMIN)",
            description = "Sert silme yerine pasifleştirme (geçmiş korunur). Servis yoksa 404.")
    public ServiceResponse setActive(@PathVariable Long id,
            @Valid @RequestBody ServiceActiveRequest request) {
        return serviceCommandService.setActive(id, request.activeState());
    }
}
