package br.unipar.foodservice.dtos;

import br.unipar.foodservice.entities.Cliente;

import java.util.List;

public record ClienteResponse(
        Long id,
        String nome,
        String telefone,
        String email,
        String linkWhatsApp,
        List<EnderecoClienteResponse> enderecos,
        Boolean ativo
) {
    public static ClienteResponse from(Cliente c) {
        List<EnderecoClienteResponse> enderecos = c.getEnderecos() == null
                ? List.of()
                : c.getEnderecos().stream()
                    .map(EnderecoClienteResponse::from)
                    .toList();
        return new ClienteResponse(
                c.getId(),
                c.getNome(),
                c.getTelefone(),
                c.getEmail(),
                "https://wa.me/" + c.getTelefone(),
                enderecos,
                c.getAtivo());
    }
}
