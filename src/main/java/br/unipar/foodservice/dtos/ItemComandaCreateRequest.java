package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ItemComandaCreateRequest(
        @NotNull Long produtoId,

        @NotNull
        @DecimalMin(value = "0.001", message = "quantidade deve ser > 0")
        BigDecimal quantidade,

        @DecimalMin(value = "0.00", message = "valorDesconto não pode ser negativo")
        BigDecimal valorDesconto,

        @DecimalMin(value = "0.00", message = "valorAcrescimo não pode ser negativo")
        BigDecimal valorAcrescimo,

        @Size(max = 500)
        String observacao
) {
}
