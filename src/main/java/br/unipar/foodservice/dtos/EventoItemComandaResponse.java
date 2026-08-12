package br.unipar.foodservice.dtos;

import br.unipar.foodservice.entities.EventoItemComanda;
import br.unipar.foodservice.enums.AcaoEventoItem;
import br.unipar.foodservice.enums.MotivoCancelamentoItem;

import java.time.LocalDateTime;

public record EventoItemComandaResponse(
        Long id,
        Long itemComandaId,
        AcaoEventoItem acao,
        Long usuarioId,
        String usuarioLogin,
        String usuarioNome,
        MotivoCancelamentoItem motivo,
        String valorAntes,
        String valorDepois,
        LocalDateTime dataHora
) {
    public static EventoItemComandaResponse from(EventoItemComanda e) {
        return new EventoItemComandaResponse(
                e.getId(),
                e.getItemComanda() == null ? null : e.getItemComanda().getId(),
                e.getAcao(),
                e.getUsuario() == null ? null : e.getUsuario().getId(),
                e.getUsuario() == null ? null : e.getUsuario().getLogin(),
                e.getUsuario() == null ? null : e.getUsuario().getNome(),
                e.getMotivo(),
                e.getValorAntes(),
                e.getValorDepois(),
                e.getDataHora()
        );
    }
}
