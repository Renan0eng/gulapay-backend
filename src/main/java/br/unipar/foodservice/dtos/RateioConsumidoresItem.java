package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Marca quem consumiu um item da comanda — estratégia POR_ITEM. Um item
 * pode ter mais de um consumidor (ex.: pizza dividida entre 3).
 */
public record RateioConsumidoresItem(
        @NotNull Long itemComandaId,
        @NotEmpty List<Long> comandaIndividualIds
) {
}
