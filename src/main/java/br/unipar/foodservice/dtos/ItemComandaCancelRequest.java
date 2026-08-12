package br.unipar.foodservice.dtos;

import br.unipar.foodservice.enums.MotivoCancelamentoItem;
import jakarta.validation.constraints.NotNull;

/**
 * Cancelamento de um item. Motivo é obrigatório (RN-ITEM-02 do BACKLOG).
 * O cancelamento não afeta estoque (a baixa só acontece no fechamento da
 * comanda — Opção 1, sessão 6 do CLAUDE.md).
 */
public record ItemComandaCancelRequest(
        @NotNull MotivoCancelamentoItem motivo
) {
}
