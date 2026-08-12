package br.unipar.foodservice.enums;

/**
 * Canal pelo qual uma {@code Comanda} foi originada (seção 3.4 do CLAUDE.md):
 *
 * <ul>
 *   <li>{@code MESA} — atendimento presencial com garçom; exige
 *       {@code mesaId} + {@code garcomId} na criação.</li>
 *   <li>{@code BALCAO} — pedido feito direto no balcão. Engloba também a
 *       retirada/takeaway (decisão da Sessão 17 — não há canal próprio
 *       para retirada).</li>
 *   <li>{@code DELIVERY} — pedido online via catálogo, entregue por
 *       entregador interno. Exige {@code enderecoEntregaId} ativo do
 *       cliente. Engloba também o catálogo público (Sessão 17 — não há
 *       canal próprio para online).</li>
 * </ul>
 */
public enum TipoOrigemComanda {
    MESA,
    BALCAO,
    DELIVERY
}
