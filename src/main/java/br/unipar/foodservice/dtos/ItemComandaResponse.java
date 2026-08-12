package br.unipar.foodservice.dtos;

import br.unipar.foodservice.entities.ItemComanda;
import br.unipar.foodservice.enums.MotivoCancelamentoItem;
import br.unipar.foodservice.enums.StatusItemComanda;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemComandaResponse(
        Long id,
        Long comandaId,

        Long produtoId,
        String produtoNome,
        BigDecimal precoUnitario,

        BigDecimal quantidade,
        BigDecimal valorDesconto,
        BigDecimal valorAcrescimo,
        BigDecimal subtotal,

        StatusItemComanda status,
        MotivoCancelamentoItem motivoCancelamento,

        String observacao,

        Long itemOrigemId,

        Long lancadoPorId,
        String lancadoPorNome,

        LocalDateTime dataLancamento,
        LocalDateTime dataStatus
) {
    public static ItemComandaResponse from(ItemComanda i) {
        return new ItemComandaResponse(
                i.getId(),
                i.getComanda() == null ? null : i.getComanda().getId(),
                i.getProduto() == null ? null : i.getProduto().getId(),
                i.getProduto() == null ? null : i.getProduto().getNome(),
                i.getPrecoUnitario(),
                i.getQuantidade(),
                i.getValorDesconto(),
                i.getValorAcrescimo(),
                i.getSubtotal(),
                i.getStatus(),
                i.getMotivoCancelamento(),
                i.getObservacao(),
                i.getItemOrigem() == null ? null : i.getItemOrigem().getId(),
                i.getLancadoPor() == null ? null : i.getLancadoPor().getId(),
                i.getLancadoPor() == null ? null : i.getLancadoPor().getNome(),
                i.getDataLancamento(),
                i.getDataStatus()
        );
    }
}
