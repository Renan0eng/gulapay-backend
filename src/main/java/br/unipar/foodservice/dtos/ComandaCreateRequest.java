package br.unipar.foodservice.dtos;

import br.unipar.foodservice.enums.EscopoComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação de Comanda.
 *
 * <p>Regras (validadas pelo {@code ComandaService}, RN-COM-01..07 do BACKLOG):
 * <ul>
 *   <li>{@code clienteId} obrigatório em todos os canais (RF20).</li>
 *   <li>{@code MESA}: {@code mesaId} e {@code garcomId} obrigatórios.</li>
 *   <li>{@code BALCAO}/{@code DELIVERY}: escopo é <b>forçado</b> para
 *       {@code INDIVIDUAL} pelo service (qualquer valor no request é
 *       ignorado).</li>
 *   <li>{@code DELIVERY}: {@code enderecoEntregaId} obrigatório e precisa
 *       pertencer ao cliente.</li>
 *   <li>{@code comandaPaiId}: usado quando esta é uma INDIVIDUAL filha de
 *       uma COMPARTILHADA da mesma mesa.</li>
 * </ul>
 */
public record ComandaCreateRequest(
        @NotNull TipoOrigemComanda tipoOrigem,

        /** Quando ausente em MESA, o service usa {@code INDIVIDUAL} como default. */
        EscopoComanda escopo,

        @NotNull Long clienteId,

        Long mesaId,
        Long garcomId,
        Long enderecoEntregaId,
        Long comandaPaiId,

        @Size(max = 500)
        String observacao
) {
}
