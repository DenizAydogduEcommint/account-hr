package com.ecommint.accounthr.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.dto.CardDto;
import com.ecommint.accounthr.repository.CardRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Kart referans uçları (E3-02) — Servisler ekranındaki kart seçimi (dropdown) için.
 *
 * <p>Tanımlı kartlar: Akbank Axess {@code ****3800}, YKB Ticari {@code ****3909},
 * Ziraat Bankkart {@code ****9164}. Salt-okunur; herhangi bir kimlik doğrulanmış
 * kullanıcıya açık. Entity sızdırmaz (temiz {@link CardDto}).
 */
@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards", description = "Kart referans listesi — Servisler ekranı kart seçimi")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardRepository cardRepository;

    public CardController(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    /** Tüm kartları referans olarak listele (son-4-haneye göre sıralı). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kartları listele (referans)",
            description = "Servisler ekranı kart seçimi için id + son4 + banka + sahip. "
                    + "Kimlik doğrulama gerektirir.")
    public List<CardDto> list() {
        return cardRepository.findAll().stream()
                .sorted(Comparator.comparing(Card::getLastFour,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CardController::toDto)
                .toList();
    }

    private static CardDto toDto(Card c) {
        return new CardDto(c.getId(), c.getLastFour(), c.getBank(), c.getHolderName(), c.getLabel());
    }
}
