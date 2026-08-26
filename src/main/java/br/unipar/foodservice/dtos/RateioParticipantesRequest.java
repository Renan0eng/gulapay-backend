package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Define, de forma declarativa, quais comandas {@code INDIVIDUAL} da mesma
 * mesa participam do rateio da {@code COMPARTILHADA}. Toda individual cujo
 * id não estiver na lista é marcada como não-participante.
 */
public record RateioParticipantesRequest(
        @NotNull List<Long> comandaIndividualIds
) {
}
