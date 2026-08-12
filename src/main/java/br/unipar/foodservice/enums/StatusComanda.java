package br.unipar.foodservice.enums;

/**
 * Estados de uma {@code Comanda} no ciclo de vida.
 *
 * <p>Transições válidas (Sprint 2):
 * <pre>
 *   ABERTA ──fechar()────────────→ AGUARDANDO_PAGAMENTO
 *   ABERTA ──cancelar()──────────→ CANCELADA
 *   AGUARDANDO_PAGAMENTO ──reabrir() (ADM)──→ ABERTA
 *   AGUARDANDO_PAGAMENTO ──pagar() (Sprint 4)──→ FECHADA
 * </pre>
 *
 * <p>Na Sprint 2 expomos apenas até {@code AGUARDANDO_PAGAMENTO}. A
 * transição final para {@code FECHADA} acontece na Sprint 4 (Pagamento).
 */
public enum StatusComanda {
    ABERTA,
    AGUARDANDO_PAGAMENTO,
    FECHADA,
    CANCELADA
}
