package br.unipar.foodservice.enums;

/**
 * Ações registradas em {@code EventoItemComanda} (auditoria — RNF09).
 *
 * <p>Toda mutação em {@code ItemComanda} cria um evento com a ação
 * correspondente, o usuário autenticado, motivo (se aplicável) e
 * snapshots JSON dos valores antes/depois.
 */
public enum AcaoEventoItem {
    /** Item lançado pela primeira vez na comanda. */
    CRIADO,
    /** Quantidade, desconto, acréscimo ou observação alterados. */
    EDITADO,
    /** Item movido para outra comanda (mesma mesa) — par com CRIADO no destino. */
    TRANSFERIDO,
    /** Item cancelado com motivo (sem baixa de estoque). */
    CANCELADO,
    /** Item marcado como entregue ao cliente. */
    ENTREGUE
}
