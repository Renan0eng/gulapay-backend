package br.unipar.foodservice.dtos;

import br.unipar.foodservice.entities.EnderecoCliente;

public record EnderecoClienteResponse(
        Long id,
        Long clienteId,
        String rotulo,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        String cep,
        String referencia,
        Boolean principal,
        Boolean ativo
) {
    public static EnderecoClienteResponse from(EnderecoCliente e) {
        return new EnderecoClienteResponse(
                e.getId(),
                e.getCliente() == null ? null : e.getCliente().getId(),
                e.getRotulo(),
                e.getLogradouro(),
                e.getNumero(),
                e.getComplemento(),
                e.getBairro(),
                e.getCidade(),
                e.getUf(),
                e.getCep(),
                e.getReferencia(),
                e.getPrincipal(),
                e.getAtivo()
        );
    }
}
