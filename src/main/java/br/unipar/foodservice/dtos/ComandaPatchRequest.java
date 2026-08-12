package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.Size;

/**
 * Atualização parcial de Comanda. Apenas campos editáveis em uma comanda
 * já criada (não permite alterar canal, escopo, cliente, mesa, etc — para
 * isso, cancele e recrie).
 *
 * <p>Permissões aplicadas no service:
 * <ul>
 *   <li>{@code observacao}: garçom dono, caixa, admin.</li>
 *   <li>{@code garcomId}: apenas caixa/admin.</li>
 *   <li>{@code enderecoEntregaId}: apenas caixa/admin (para DELIVERY).</li>
 * </ul>
 */
public record ComandaPatchRequest(
        @Size(max = 500)
        String observacao,

        Long garcomId,

        Long enderecoEntregaId
) {
}
