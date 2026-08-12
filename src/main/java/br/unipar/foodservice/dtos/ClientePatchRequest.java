package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Atualização parcial de Cliente. Semântica: cada campo {@code null} = "não altera".
 *
 * <p>Desde a Sprint 2 (Sessão 17), endereços não fazem mais parte deste
 * payload — usar os endpoints {@code /clientes/{id}/enderecos} e
 * {@code /enderecos/{id}}.
 */
public record ClientePatchRequest(
        @Size(min = 2, max = 120)
        String nome,

        @Pattern(regexp = "^\\+?[0-9 ()\\-]{8,20}$",
                 message = "telefone deve conter de 8 a 20 caracteres válidos")
        String telefone,

        @Email @Size(max = 120)
        String email,

        Boolean ativo
) {
}
