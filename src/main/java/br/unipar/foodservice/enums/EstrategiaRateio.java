package br.unipar.foodservice.enums;

/**
 * Estratégia de rateio de uma {@code Comanda COMPARTILHADA} entre as
 * {@code INDIVIDUAIS} participantes (Sprint 3 — seção 5.2 do CLAUDE.md).
 *
 * <ul>
 *   <li>{@code SEM_RATEIO} — uma única individual quita a compartilhada inteira.</li>
 *   <li>{@code IGUALITARIO} — total dividido em partes iguais entre os participantes.</li>
 *   <li>{@code MANUAL} — Caixa informa o valor de cada participante (soma deve fechar com o total).</li>
 *   <li>{@code PROPORCIONAL} — ponderado pelo total consumido (itens próprios) de cada participante.</li>
 *   <li>{@code POR_ITEM} — cada item é atribuído a um ou mais consumidores e rateado individualmente.</li>
 * </ul>
 */
public enum EstrategiaRateio {
    SEM_RATEIO,
    IGUALITARIO,
    MANUAL,
    PROPORCIONAL,
    POR_ITEM
}
