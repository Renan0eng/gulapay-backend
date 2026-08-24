package br.unipar.foodservice.dtos;

import br.unipar.foodservice.entities.Comanda;

import java.math.BigDecimal;

/** Uma linha de {@link RateioComandaResponse#participantes()}. */
public record RateioParticipanteResponse(
        Long comandaIndividualId,
        String codigo,
        String clienteNome,
        boolean participante,
        BigDecimal valorProprio,
        BigDecimal valorRateio,
        BigDecimal valorTotal
) {
    public static RateioParticipanteResponse from(Comanda individual) {
        BigDecimal proprio = individual.getTotalLiquido() == null ? BigDecimal.ZERO : individual.getTotalLiquido();
        BigDecimal rateio = individual.getValorRateio() == null ? BigDecimal.ZERO : individual.getValorRateio();
        return new RateioParticipanteResponse(
                individual.getId(),
                individual.getCodigo(),
                individual.getCliente() == null ? null : individual.getCliente().getNome(),
                Boolean.TRUE.equals(individual.getParticipanteRateio()),
                proprio,
                rateio,
                proprio.add(rateio)
        );
    }
}
