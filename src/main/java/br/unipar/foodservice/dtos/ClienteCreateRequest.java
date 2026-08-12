package br.unipar.foodservice.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload de criação de Cliente.
 *
 * <p>Desde a Sprint 2 (Sessão 17), o endereço embutido único deu lugar a uma
 * lista opcional {@code enderecos}: cada item segue
 * {@link EnderecoClienteRequest} e segue o padrão "embutido" (cf. Sessão 16
 * — Insumo embutido em Produto). Regras:
 *
 * <ul>
 *   <li>{@code null} ou lista vazia: cliente é criado sem endereços (válido
 *       para BALCAO/MESA).</li>
 *   <li>Lista com 1 item: o endereço é automaticamente marcado como
 *       principal=true.</li>
 *   <li>Lista com ≥2 itens: exatamente um precisa ter principal=true. Caso
 *       contrário, {@link br.unipar.foodservice.exceptions.BusinessException}.</li>
 * </ul>
 */
public record ClienteCreateRequest(
        @NotBlank @Size(min = 2, max = 120)
        String nome,

        @NotBlank
        @Pattern(regexp = "^\\+?[0-9 ()\\-]{8,20}$",
                 message = "telefone deve conter de 8 a 20 caracteres válidos (dígitos, +, -, espaços, parênteses)")
        String telefone,

        @Email @Size(max = 120)
        String email,

        @Valid
        List<EnderecoClienteRequest> enderecos
) {
}
