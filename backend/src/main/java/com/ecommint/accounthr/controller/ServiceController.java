package com.ecommint.accounthr.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.dto.PagedResponse;
import com.ecommint.accounthr.dto.service.ServiceDto;
import com.ecommint.accounthr.service.ServiceQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Servis (abonelik) uçları (E1-07 referans endpoint).
 *
 * <p>Bu controller projenin REST standardının canlı örneğidir: çoğul kaynak adı
 * ({@code /api/v1/services}), {@link Pageable} ile sayfalama/sıralama, entity
 * sızdırmayan temiz {@link ServiceDto}, {@link PagedResponse} zarfı ve Swagger
 * dokümantasyonu. Salt-okunur; {@code authenticated()} gerektirir.
 */
@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "Services", description = "Servis (abonelik) master listesi — sayfalı, salt-okunur")
@SecurityRequirement(name = "bearerAuth")
public class ServiceController {

    private final ServiceQueryService serviceQueryService;

    public ServiceController(ServiceQueryService serviceQueryService) {
        this.serviceQueryService = serviceQueryService;
    }

    /**
     * Servisleri sayfalı listele. Sorgu parametreleri: {@code ?page=&size=&sort=}
     * (ör. {@code ?page=0&size=20&sort=name,asc}).
     */
    @GetMapping
    @Operation(
            summary = "Servisleri listele (sayfalı)",
            description = "Standart sayfalama/sıralama: ?page=&size=&sort=name,asc. "
                    + "Yanıt PagedResponse<ServiceDto> zarfıdır. Kimlik doğrulama gerektirir.")
    public PagedResponse<ServiceDto> list(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return serviceQueryService.list(pageable);
    }
}
