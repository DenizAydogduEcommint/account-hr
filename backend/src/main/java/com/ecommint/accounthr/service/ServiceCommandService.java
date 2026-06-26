package com.ecommint.accounthr.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.dto.service.ServiceContactRequest;
import com.ecommint.accounthr.dto.service.ServiceRequest;
import com.ecommint.accounthr.dto.service.ServiceResponse;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * Servis (abonelik) yazma katmanı (E3-02) — create/update/deactivate.
 *
 * <p>Sağlayıcı <b>resolve-or-create</b> (E2-02 mantığı): aynı isimli sağlayıcı
 * (büyük/küçük harf duyarsız) varsa yeniden kullanılır, yoksa oluşturulur. Kart
 * son-4-haneye göre çözülür ({@code findByLastFour}); eşleşmezse {@code null}.
 *
 * <p>Sert silme (hard DELETE) YOKTUR; geçmiş veriyle ilişki korunsun diye servis
 * yalnızca pasifleştirilir ({@link #setActive(Long, ActiveState)}).
 *
 * <p>İletişim kayıtları update'te <b>tamamen değiştirilir</b> (eskiler silinip
 * yenileri yazılır) — basit ve öngörülebilir; ilk {@code primary} işaretli (yoksa
 * ilk kayıt) birincil sayılır.
 */
@Service
public class ServiceCommandService {

    private final ServiceRepository serviceRepository;
    private final ProviderRepository providerRepository;
    private final CardRepository cardRepository;
    private final ServiceContactRepository serviceContactRepository;
    private final ServiceQueryService serviceQueryService;

    public ServiceCommandService(ServiceRepository serviceRepository,
            ProviderRepository providerRepository,
            CardRepository cardRepository,
            ServiceContactRepository serviceContactRepository,
            ServiceQueryService serviceQueryService) {
        this.serviceRepository = serviceRepository;
        this.providerRepository = providerRepository;
        this.cardRepository = cardRepository;
        this.serviceContactRepository = serviceContactRepository;
        this.serviceQueryService = serviceQueryService;
    }

    @Transactional
    public ServiceResponse create(ServiceRequest req) {
        com.ecommint.accounthr.domain.Service service = new com.ecommint.accounthr.domain.Service();
        applyRequest(service, req);
        service = serviceRepository.save(service);
        replaceContacts(service, req.contacts());
        return serviceQueryService.toResponse(service);
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest req) {
        com.ecommint.accounthr.domain.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servis bulunamadı: " + id));
        applyRequest(service, req);
        service = serviceRepository.save(service);
        replaceContacts(service, req.contacts());
        return serviceQueryService.toResponse(service);
    }

    @Transactional
    public ServiceResponse setActive(Long id, ActiveState activeState) {
        com.ecommint.accounthr.domain.Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servis bulunamadı: " + id));
        service.setActiveState(activeState);
        service = serviceRepository.save(service);
        return serviceQueryService.toResponse(service);
    }

    private void applyRequest(com.ecommint.accounthr.domain.Service service, ServiceRequest req) {
        service.setName(req.name());
        service.setProvider(resolveOrCreateProvider(req.providerName()));
        service.setDefaultCard(resolveCard(req.cardLast4()));
        service.setFrequency(req.frequency() != null ? req.frequency() : Frequency.MONTHLY);
        service.setActiveState(req.activeState() != null ? req.activeState() : ActiveState.UNCERTAIN);
        service.setActiveMonths(req.activeMonths());
        service.setApproxAmountTry(req.approxAmountTry());
        // Yalnızca istek alanı doluysa güncelle. Aksi halde PUT bu bayrağı sessizce
        // false'a sıfırlar (Multinet/Sigorta bilgi-amaçlı servislerde veri kaybı). Create
        // yolunda entity varsayılanı zaten false olduğundan null güvenlidir (E3-02 düzeltmesi).
        if (req.informational() != null) {
            service.setInformational(req.informational());
        }
        service.setInvoiceSource(req.invoiceSource());
        service.setPurpose(req.purpose());
        service.setNotes(req.notes());
    }

    /**
     * Sağlayıcı resolve-or-create (E2-02). İsim boşsa "(Bilinmeyen)" kullanılır
     * (provider {@code optional = false}). Büyük/küçük harf duyarsız eşleme.
     *
     * <p>Idempotent: eşzamanlı aynı-isim oluşturmalarda iki istek de
     * {@code findByNameIgnoreCase} ile boş görüp insert deneyebilir; biri
     * unique-constraint'e takılır. {@code saveAndFlush} ile çakışmayı HEMEN
     * yüzeye çıkarıp {@link DataIntegrityViolationException}'ı yakalar, var olan
     * satırı yeniden sorgulayıp döneriz — 500 yerine mevcut sağlayıcı (E3-02).
     */
    private Provider resolveOrCreateProvider(String name) {
        String providerName = StringUtils.hasText(name) ? name.trim() : "(Bilinmeyen)";
        return providerRepository.findByNameIgnoreCase(providerName)
                .orElseGet(() -> {
                    Provider p = new Provider();
                    p.setName(providerName);
                    try {
                        return providerRepository.saveAndFlush(p);
                    } catch (DataIntegrityViolationException ex) {
                        // Yarış: başka bir işlem aynı ismi araya soktu — mevcut satırı dön.
                        return providerRepository.findByNameIgnoreCase(providerName)
                                .orElseThrow(() -> ex);
                    }
                });
    }

    /** Kart son-4-haneye göre çöz; girdi yoksa/eşleşmezse {@code null}. */
    private Card resolveCard(String cardLast4) {
        if (!StringUtils.hasText(cardLast4)) {
            return null;
        }
        return cardRepository.findByLastFour(cardLast4.trim()).orElse(null);
    }

    /**
     * Servisin iletişim kayıtlarını tamamen yeniden yazar. E-posta alanı virgüllü
     * çoklu adres olabilir; verbatim saklanır (E6 hatırlatma birden çok alıcıya
     * gidebilsin). İlk {@code primary} işaretli kayıt (yoksa ilk kayıt) birincil.
     */
    private void replaceContacts(com.ecommint.accounthr.domain.Service service,
            List<ServiceContactRequest> contacts) {
        serviceContactRepository.deleteAll(serviceContactRepository.findByServiceId(service.getId()));
        if (contacts == null || contacts.isEmpty()) {
            return;
        }
        boolean primaryAssigned = false;
        for (ServiceContactRequest cr : contacts) {
            if (!StringUtils.hasText(cr.email())) {
                continue;
            }
            ServiceContact contact = new ServiceContact();
            contact.setService(service);
            contact.setEmail(cr.email().trim());
            contact.setSource(cr.source());
            boolean wantsPrimary = Boolean.TRUE.equals(cr.primary());
            if (wantsPrimary && !primaryAssigned) {
                contact.setPrimary(true);
                primaryAssigned = true;
            }
            serviceContactRepository.save(contact);
        }
        // Hiç primary işaretlenmediyse ilk kaydı birincil yap.
        if (!primaryAssigned) {
            List<ServiceContact> saved = serviceContactRepository.findByServiceId(service.getId());
            if (!saved.isEmpty()) {
                ServiceContact first = saved.get(0);
                first.setPrimary(true);
                serviceContactRepository.save(first);
            }
        }
    }
}
