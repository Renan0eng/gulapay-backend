package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Atualização parcial de um item. Cada campo {@code null} = "não altera".
 *
 * <p>Permitida apenas enquanto o item está em
 * {@link br.unipar.foodservice.enums.StatusItemComanda#EM_PREPARO}
 * (regra 5.2.1 do CLAUDE.md). Em qualquer outro status retorna 422.
 */
public record ItemComandaUpdateRequest(
        @DecimalMin(value = "0.001", message = "quantidade deve ser > 0")
        BigDecimal quantidade,

        @DecimalMin(value = "0.00")
        BigDecimal valorDesconto,

        @DecimalMin(value = "0.00")
        BigDecimal valorAcrescimo,

        @Size(max = 500)
        String observacao
) {
}
