package br.unipar.foodservice.enums;

/**
 * Motivos válidos para cancelar um {@code ItemComanda} (seção 5.2.1 do CLAUDE.md).
 *
 * <ul>
 *   <li>{@code LANCAMENTO_INCORRETO} — garçom/caixa lançou produto errado.</li>
 *   <li>{@code CLIENTE_DESISTIU} — cliente desistiu do item antes da entrega.</li>
 *   <li>{@code CORTESIA} — cancelamento como gentileza/promoção (não cobra).</li>
 *   <li>{@code ERRO_PRODUCAO} — produção errou (alimento queimado, etc).</li>
 * </ul>
 *
 * <p>O motivo é obrigatório em qualquer cancelamento (RN-ITEM-02).
 */
public enum MotivoCancelamentoItem {
    LANCAMENTO_INCORRETO,
    CLIENTE_DESISTIU,
    CORTESIA,
    ERRO_PRODUCAO
}
