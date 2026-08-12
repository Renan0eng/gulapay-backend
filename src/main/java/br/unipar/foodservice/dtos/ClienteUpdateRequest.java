package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de atualização (PUT) de Cliente.
 *
 * <p>Desde a Sprint 2 (Sessão 17), endereços são gerenciados em endpoints
 * próprios ({@code /clientes/{id}/enderecos} e {@code /enderecos/{id}}) e
 * não fazem mais parte deste payload — princípio do menor surpreendente
 * (edição do cliente não cria/altera entidades filhas).
 */
public record ClienteUpdateRequest(
        @NotBlank @Size(min = 2, max = 120)
        String nome,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 ()\\-]{8,20}$")
        String telefone,

        @Email @Size(max = 120)
        String email,

        @NotNull
        Boolean ativo
) {
}
