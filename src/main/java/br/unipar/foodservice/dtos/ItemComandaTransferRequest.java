package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.NotNull;

/**
 * Transferência de um item para outra comanda. Nesta sprint, só é
 * permitido transferir entre comandas <b>da mesma mesa</b>
 * (regra RN-ITEM-01 do BACKLOG). Em BALCAO/DELIVERY, transferência
 * fica bloqueada com 422.
 */
public record ItemComandaTransferRequest(
        @NotNull Long comandaDestinoId
) {
}
