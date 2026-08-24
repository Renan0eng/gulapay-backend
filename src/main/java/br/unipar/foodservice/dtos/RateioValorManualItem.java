package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Um item de {@link RateioComandaRequest#valoresManuais()} — estratégia MANUAL. */
public record RateioValorManualItem(
        @NotNull Long comandaIndividualId,

        @NotNull
        @DecimalMin(value = "0.00", message = "valor não pode ser negativo")
        BigDecimal valor
) {
}
