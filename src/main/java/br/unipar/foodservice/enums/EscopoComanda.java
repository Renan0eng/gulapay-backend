package br.unipar.foodservice.enums;

/**
 * Escopo de uma {@code Comanda} (seção 5.2 do CLAUDE.md — Modelo A):
 *
 * <ul>
 *   <li>{@code COMPARTILHADA} — 0..1 por mesa. Agrega itens consumidos em
 *       conjunto e, no fechamento, é rateada entre as
 *       {@code INDIVIDUAL} vinculadas (Sprint 3).</li>
 *   <li>{@code INDIVIDUAL} — 0..N por mesa em MESA, e única forma usada
 *       em BALCAO/DELIVERY (forçada pelo service nesses dois canais).</li>
 * </ul>
 */
public enum EscopoComanda {
    COMPARTILHADA,
    INDIVIDUAL
}
