package br.unipar.foodservice.enums;

/**
 * Estados de um {@code ItemComanda} (seção 5.2.1 do CLAUDE.md).
 *
 * <p>Item nasce em {@code EM_PREPARO} (sem etapa manual de envio à
 * produção — impressão térmica dispara automaticamente). Garçom marca
 * {@code ENTREGUE} ao servir. {@code CANCELADO} e {@code TRANSFERIDO} são
 * estados terminais.
 *
 * <p>{@code TRANSFERIDO} marca o item de origem após uma transferência —
 * um novo item é criado na comanda de destino com
 * {@code itemOrigemId} apontando para este.
 */
public enum StatusItemComanda {
    EM_PREPARO,
    ENTREGUE,
    CANCELADO,
    TRANSFERIDO
}
