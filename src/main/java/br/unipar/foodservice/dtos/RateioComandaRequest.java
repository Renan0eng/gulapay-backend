package br.unipar.foodservice.dtos;

import br.unipar.foodservice.enums.EstrategiaRateio;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Payload de {@code POST /comandas/{id}/rateio}.
 *
 * <p>{@code valoresManuais} só é lido quando {@code estrategia=MANUAL};
 * {@code itensConsumidores} só é lido quando {@code estrategia=POR_ITEM}.
 * Para as demais estratégias os dois campos são ignorados.
 */
public record RateioComandaRequest(
        @NotNull EstrategiaRateio estrategia,
        List<RateioValorManualItem> valoresManuais,
        List<RateioConsumidoresItem> itensConsumidores
) {
}
