package br.unipar.foodservice.dtos;

import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.enums.EstrategiaRateio;

import java.math.BigDecimal;
import java.util.List;

public record RateioComandaResponse(
        Long comandaId,
        String codigo,
        EstrategiaRateio estrategia,
        BigDecimal totalLiquido,
        List<RateioParticipanteResponse> participantes
) {
    public static RateioComandaResponse from(Comanda compartilhada) {
        List<RateioParticipanteResponse> participantes = compartilhada.getIndividuais() == null
                ? List.of()
                : compartilhada.getIndividuais().stream()
                        .map(RateioParticipanteResponse::from)
                        .toList();
        return new RateioComandaResponse(
                compartilhada.getId(),
                compartilhada.getCodigo(),
                compartilhada.getEstrategiaRateio(),
                compartilhada.getTotalLiquido(),
                participantes
        );
    }
}
