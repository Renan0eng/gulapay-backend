package br.unipar.foodservice.dtos;

import br.unipar.foodservice.entities.Comanda;
import br.unipar.foodservice.enums.EscopoComanda;
import br.unipar.foodservice.enums.StatusComanda;
import br.unipar.foodservice.enums.TipoOrigemComanda;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ComandaResponse(
        Long id,
        String codigo,
        TipoOrigemComanda tipoOrigem,
        EscopoComanda escopo,
        StatusComanda status,

        Long clienteId,
        String clienteNome,
        String clienteTelefone,
        String linkWhatsApp,

        Long mesaId,
        String mesaNumero,

        Long comandaPaiId,

        Long garcomId,
        String garcomNome,

        Long entregadorId,
        String entregadorNome,

        Long enderecoEntregaId,

        String observacao,

        BigDecimal totalBruto,
        BigDecimal totalDescontos,
        BigDecimal totalAcrescimos,
        BigDecimal totalLiquido,

        LocalDateTime dataAbertura,
        LocalDateTime dataFechamento,

        List<ItemComandaResponse> itens
) {
    public static ComandaResponse from(Comanda c) {
        return new ComandaResponse(
                c.getId(),
                c.getCodigo(),
                c.getTipoOrigem(),
                c.getEscopo(),
                c.getStatus(),
                c.getCliente() == null ? null : c.getCliente().getId(),
                c.getCliente() == null ? null : c.getCliente().getNome(),
                c.getCliente() == null ? null : c.getCliente().getTelefone(),
                c.getLinkWhatsApp(),
                c.getMesa() == null ? null : c.getMesa().getId(),
                c.getMesa() == null ? null : c.getMesa().getNumero(),
                c.getComandaPai() == null ? null : c.getComandaPai().getId(),
                c.getGarcom() == null ? null : c.getGarcom().getId(),
                c.getGarcom() == null ? null : c.getGarcom().getNome(),
                c.getEntregador() == null ? null : c.getEntregador().getId(),
                c.getEntregador() == null ? null : c.getEntregador().getNome(),
                c.getEnderecoEntrega() == null ? null : c.getEnderecoEntrega().getId(),
                c.getObservacao(),
                c.getTotalBruto(),
                c.getTotalDescontos(),
                c.getTotalAcrescimos(),
                c.getTotalLiquido(),
                c.getDataAbertura(),
                c.getDataFechamento(),
                c.getItens() == null
                        ? List.of()
                        : c.getItens().stream().map(ItemComandaResponse::from).toList()
        );
    }

    /** Versão enxuta — sem a lista de itens — para endpoints de listagem. */
    public static ComandaResponse summary(Comanda c) {
        return new ComandaResponse(
                c.getId(),
                c.getCodigo(),
                c.getTipoOrigem(),
                c.getEscopo(),
                c.getStatus(),
                c.getCliente() == null ? null : c.getCliente().getId(),
                c.getCliente() == null ? null : c.getCliente().getNome(),
                c.getCliente() == null ? null : c.getCliente().getTelefone(),
                c.getLinkWhatsApp(),
                c.getMesa() == null ? null : c.getMesa().getId(),
                c.getMesa() == null ? null : c.getMesa().getNumero(),
                c.getComandaPai() == null ? null : c.getComandaPai().getId(),
                c.getGarcom() == null ? null : c.getGarcom().getId(),
                c.getGarcom() == null ? null : c.getGarcom().getNome(),
                c.getEntregador() == null ? null : c.getEntregador().getId(),
                c.getEntregador() == null ? null : c.getEntregador().getNome(),
                c.getEnderecoEntrega() == null ? null : c.getEnderecoEntrega().getId(),
                c.getObservacao(),
                c.getTotalBruto(),
                c.getTotalDescontos(),
                c.getTotalAcrescimos(),
                c.getTotalLiquido(),
                c.getDataAbertura(),
                c.getDataFechamento(),
                List.of()
        );
    }
}
