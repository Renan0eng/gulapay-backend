package br.unipar.foodservice.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de criação/atualização de um endereço de Cliente.
 *
 * <p>Usado:
 * <ul>
 *   <li>Em {@code POST /clientes/{clienteId}/enderecos} e
 *       {@code PUT /enderecos/{id}};</li>
 *   <li>Como item da lista opcional em
 *       {@link ClienteCreateRequest#enderecos()} (padrão "embutido" da
 *       Sessão 16 — cria endereço junto com o cliente).</li>
 * </ul>
 *
 * <p>Campos obrigatórios: {@code logradouro}, {@code cidade}, {@code uf}.
 * Os demais são opcionais.
 */
public record EnderecoClienteRequest(

        @Size(max = 40)
        String rotulo,

        @NotBlank
        @Size(max = 150)
        String logradouro,

        @Size(max = 20)
        String numero,

        @Size(max = 80)
        String complemento,

        @Size(max = 80)
        String bairro,

        @NotBlank
        @Size(max = 80)
        String cidade,

        @NotBlank
        @Size(min = 2, max = 2)
        String uf,

        @Size(max = 10)
        String cep,

        String referencia,

        Boolean principal
) {
}
