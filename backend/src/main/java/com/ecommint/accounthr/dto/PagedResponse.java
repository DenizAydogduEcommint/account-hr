package com.ecommint.accounthr.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standart sayfalama yanıtı (E1-07). Spring {@link Page}'i UI/mobil için temiz,
 * sabit bir sözleşmeye sarar. Sorgu parametreleri: {@code ?page=&size=&sort=}.
 */
@Schema(description = "Standart sayfalı yanıt zarfı")
public record PagedResponse<T>(
        @Schema(description = "Bu sayfadaki kayıtlar")
        List<T> content,

        @Schema(description = "0-tabanlı sayfa indeksi", example = "0")
        int page,

        @Schema(description = "Sayfa boyutu", example = "20")
        int size,

        @Schema(description = "Toplam kayıt sayısı", example = "42")
        long totalElements,

        @Schema(description = "Toplam sayfa sayısı", example = "3")
        int totalPages,

        @Schema(description = "İlk sayfa mı", example = "true")
        boolean first,

        @Schema(description = "Son sayfa mı", example = "false")
        boolean last,

        @Schema(description = "Uygulanan sıralama (ör. \"name: ASC\")", example = "name: ASC")
        String sort) {

    /** İçeriği olduğu gibi tutarak bir {@link Page}'i sarar. */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return from(page, Function.identity());
    }

    /** İçeriği {@code mapper} ile dönüştürerek (entity → DTO) bir {@link Page}'i sarar. */
    public static <S, T> PagedResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return from(page, page.getContent(), mapper);
    }

    /**
     * Sayfalama METADATA'sını {@code page}'ten alır ama içeriği AYRI verilen {@code content}
     * listesinden ({@code mapper} ile) üretir. N+1 azaltma deseni için: sayfalama/count doğru
     * {@code Page} sorgusundan gelir, içerik ise ilişkileri eager-fetch'li yeniden çekilmiş
     * (ve sayfa sırasına göre dizilmiş) listeden eşlenir. {@code content} sayfa içeriğiyle
     * AYNI elemanları/sırayı taşımalıdır.
     */
    public static <S, T> PagedResponse<T> from(Page<S> page, List<S> content,
            Function<S, T> mapper) {
        return new PagedResponse<>(
                content.stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getSort().isSorted() ? page.getSort().toString() : "unsorted");
    }
}
